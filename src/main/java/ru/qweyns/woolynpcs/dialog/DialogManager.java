package ru.qweyns.woolynpcs.dialog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.api.DialogNodeEvent;
import ru.qweyns.woolynpcs.api.WoolyNpcsApi;
import ru.qweyns.woolynpcs.api.DialogStartEvent;
import ru.qweyns.woolynpcs.condition.Condition;
import ru.qweyns.woolynpcs.condition.ConditionParser;
import ru.qweyns.woolynpcs.model.ActionType;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.NpcAction;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.util.ColorUtil;
import ru.qweyns.woolynpcs.util.ResourceFolder;
import ru.qweyns.woolynpcs.util.RegistryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DialogManager {
    private final WoolyNpcs plugin;
    private final Map<String, Dialog> dialogs = new LinkedHashMap<>();
    private final Map<UUID, ActiveDialog> activeSessions = new ConcurrentHashMap<>();
    private final File dialogFolder;

    public DialogManager(WoolyNpcs plugin) {
        this.plugin = plugin;
        this.dialogFolder = new File(plugin.getDataFolder(), "dialogs");
        if (!dialogFolder.exists() && !dialogFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку dialogs/");
        }
    }

    public void reload() {
        ResourceFolder.copyDefaults(plugin, "dialogs");
        loadDialogs();
    }

    public void loadDialogs() {
        dialogs.clear();
        activeSessions.clear();

        File[] files = dialogFolder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String fileName = file.getName();
            String dialogId = fileName.substring(0, fileName.length() - ".yml".length());
            String startNode = config.getString("start", "start");

            Dialog dialog = new Dialog(dialogId, startNode);

            try {
                dialog.setMode(Dialog.Mode.valueOf(
                        config.getString("mode", "CHAT").trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Диалог '" + dialogId
                        + "': неизвестный режим '" + config.getString("mode") + "', используется CHAT.");
            }
            dialog.setGuiSize(config.getInt("gui-size", 27));
            dialog.setGuiTitle(config.getString("gui-title", ""));

            String defaultSound = config.getString("sound", "");
            int defaultTypewriter = config.getInt("typewriter", 0);

            ConfigurationSection nodesSection = config.getConfigurationSection("nodes");
            if (nodesSection == null) continue;

            for (String nodeKey : nodesSection.getKeys(false)) {
                ConfigurationSection nodeSec = nodesSection.getConfigurationSection(nodeKey);
                if (nodeSec == null) continue;

                String text = nodeSec.getString("text", "...");
                DialogNode node = new DialogNode(nodeKey, text);

                node.setSound(nodeSec.getString("sound", defaultSound));
                node.setTypewriter(nodeSec.getInt("typewriter", defaultTypewriter));
                node.setAutoGoto(nodeSec.getString("auto-goto", null));
                node.setAutoDelay(nodeSec.getInt("auto-delay", 40));

                for (String actionStr : nodeSec.getStringList("actions")) {
                    NpcAction action = parseDialogAction(dialogId, nodeKey, actionStr);
                    if (action != null) node.getActions().add(action);
                }

                ConfigurationSection choicesSec = nodeSec.getConfigurationSection("choices");
                if (choicesSec != null) {
                    for (String choiceKey : choicesSec.getKeys(false)) {
                        ConfigurationSection cs = choicesSec.getConfigurationSection(choiceKey);
                        if (cs == null) continue;
                        node.getChoices().add(loadChoice(dialogId, nodeKey, choiceKey, cs));
                    }
                }

                dialog.addNode(node);
            }

            if (dialog.getStartNode() == null) {
                plugin.getLogger().warning("Диалог '" + dialogId + "' пропущен: стартовый узел '"
                        + startNode + "' не найден в секции nodes.");
                continue;
            }

            validateTargets(dialog);

            dialogs.put(dialogId, dialog);
            plugin.getLogger().info("Loaded dialog: " + dialogId + " (" + dialog.getNodes().size() + " nodes)");
        }
    }

    private void validateTargets(Dialog dialog) {
        for (DialogNode node : dialog.getNodes().values()) {
            for (DialogChoice choice : node.getChoices()) {
                String target = choice.getTargetNodeId();
                if (target == null || target.isEmpty() || target.equalsIgnoreCase("close")) continue;
                if (dialog.getNode(target) == null) {
                    plugin.getLogger().warning("Диалог '" + dialog.getId() + "', узел '" + node.getId()
                            + "': вариант ведёт на несуществующий узел '" + target + "'.");
                }
            }
        }
    }

    private DialogChoice loadChoice(String dialogId, String nodeKey, String choiceKey,
                                    ConfigurationSection cs) {
        DialogChoice choice = new DialogChoice(
                cs.getString("label", choiceKey),
                cs.getString("goto", ""),
                cs.getString("permission", null));

        for (String raw : cs.getStringList("conditions")) {
            Condition condition = ConditionParser.parse(raw);
            if (condition == null) {
                plugin.getLogger().warning("Диалог '" + dialogId + "', узел '" + nodeKey
                        + "', вариант '" + choiceKey + "': не удалось разобрать условие '" + raw + "'.");
                continue;
            }
            choice.getConditions().add(condition);
        }

        for (String raw : cs.getStringList("actions")) {
            NpcAction action = parseDialogAction(dialogId, nodeKey, raw);
            if (action != null) choice.getActions().add(action);
        }

        choice.setHideIfLocked(cs.getBoolean("hide-if-locked", true));
        choice.setLockedLabel(cs.getString("locked-label", ""));
        choice.setMaterial(cs.getString("material", "PAPER"));
        choice.setSlot(cs.getInt("slot", -1));
        return choice;
    }

    private NpcAction parseDialogAction(String dialogId, String nodeKey, String actionStr) {
        if (actionStr == null) return null;
        String[] parts = actionStr.split(":", 2);
        if (parts.length < 2) {
            plugin.getLogger().warning("Диалог '" + dialogId + "', узел '" + nodeKey
                    + "': действие '" + actionStr + "' записано без типа.");
            return null;
        }
        String typeName = parts[0].trim().toUpperCase(Locale.ROOT);
        NpcAction action;
        try {
            action = new NpcAction(ClickType.ANY, ActionType.valueOf(typeName), parts[1]);
        } catch (IllegalArgumentException e) {
            if (!WoolyNpcsApi.isRegistered(typeName)) {
                plugin.getLogger().warning("Диалог '" + dialogId + "', узел '" + nodeKey
                        + "': неизвестный тип действия '" + parts[0] + "'.");
                return null;
            }
            action = NpcAction.custom(java.util.UUID.randomUUID(), ClickType.ANY, typeName, parts[1]);
        }
        return action.isEmpty() ? null : action;
    }

    public void startDialog(Player player, String dialogId, WoolyNpc npc) {
        Dialog dialog = dialogs.get(dialogId);
        if (dialog == null) return;

        DialogNode startNode = dialog.getStartNode();
        if (startNode == null) return;

        DialogStartEvent event = new DialogStartEvent(player, dialogId, npc);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        ActiveDialog session = new ActiveDialog(dialogId, startNode.getId(),
                npc == null ? null : npc.getId());
        activeSessions.put(player.getUniqueId(), session);
        showNode(player, session, dialog, startNode);
    }

    public void handleChoice(Player player, int choiceIndex) {
        ActiveDialog session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.isExpired(plugin.getConfigManager().getDialogTimeoutSeconds())) {
            activeSessions.remove(player.getUniqueId());
            player.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("dialog-expired")));
            return;
        }

        if (!isNearNpc(player, session)) {
            activeSessions.remove(player.getUniqueId());
            player.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("dialog-too-far")));
            return;
        }

        Dialog dialog = dialogs.get(session.getDialogId());
        if (dialog == null) { activeSessions.remove(player.getUniqueId()); return; }

        DialogNode currentNode = dialog.getNode(session.getCurrentNodeId());
        if (currentNode == null) { activeSessions.remove(player.getUniqueId()); return; }

        if (choiceIndex < 0 || choiceIndex >= currentNode.getChoices().size()) return;

        DialogChoice choice = currentNode.getChoices().get(choiceIndex);

        if (!choice.isAvailable(player)) {
            player.sendMessage(ColorUtil.format(
                    plugin.getConfigManager().getMessage("dialog-choice-unavailable")));
            return;
        }

        WoolyNpc npc = session.getNpcId() == null
                ? null : plugin.getNpcManager().getNpcById(session.getNpcId());
        for (NpcAction action : choice.getActions()) {
            action.execute(player, npc);
        }

        goToNode(player, session, dialog, choice.getTargetNodeId());
    }

    private boolean isNearNpc(Player player, ActiveDialog session) {
        UUID npcId = session.getNpcId();
        if (npcId == null) return true;

        WoolyNpc npc = plugin.getNpcManager().getNpcById(npcId);
        if (npc == null) return false;

        double maxDistance = plugin.getConfigManager().getDialogMaxDistance();
        if (maxDistance <= 0) return true;

        Location npcLoc = plugin.getNpcManager().effectiveLocation(npc);
        if (npcLoc.getWorld() == null || !npcLoc.getWorld().equals(player.getWorld())) return false;
        return npcLoc.distanceSquared(player.getLocation()) <= maxDistance * maxDistance;
    }

    private void showNode(Player player, ActiveDialog session, Dialog dialog, DialogNode node) {
        WoolyNpc npc = session.getNpcId() == null
                ? null : plugin.getNpcManager().getNpcById(session.getNpcId());

        boolean firstVisit = !session.wasNodeExecuted(node.getId());

        DialogNodeEvent event = new DialogNodeEvent(player, session.getDialogId(), node.getId(), npc, firstVisit);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        playNodeSound(player, node);

        if (session.markNodeExecuted(node.getId())) {
            for (NpcAction action : node.getActions()) {
                action.execute(player, npc);
            }
        }

        String npcText = applyVariables(player, node.getText());
        session.nextRender();

        if (node.getTypewriter() > 0) {
            startTypewriter(player, session, dialog, node, npcText, session.getRenderId());
            return;
        }

        renderNode(player, session, dialog, node, npcText);
    }

    private void renderNode(Player player, ActiveDialog session, Dialog dialog,
                            DialogNode node, String npcText) {
        if (dialog.getMode() == Dialog.Mode.GUI) {
            openGui(player, session, dialog, node, npcText);
        } else {
            renderChat(player, session, node, npcText);
        }

        scheduleAutoAdvance(player, session, dialog, node);

        if (node.getChoices().isEmpty() && !node.hasAutoGoto()) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    private void renderChat(Player player, ActiveDialog session, DialogNode node, String npcText) {
        player.sendMessage(Component.empty());
        player.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("dialog-npc-text",
                "text", npcText)));

        for (int i = 0; i < node.getChoices().size(); i++) {
            DialogChoice choice = node.getChoices().get(i);
            boolean available = choice.isAvailable(player);

            if (!available && choice.isHideIfLocked()) continue;

            String label = available ? choice.getLabel()
                    : (choice.getLockedLabel().isEmpty() ? choice.getLabel() : choice.getLockedLabel());

            String choiceText = plugin.getConfigManager().getMessage(
                    available ? "dialog-choice-format" : "dialog-choice-locked",
                    "index", String.valueOf(i + 1), "label", applyVariables(player, label));

            Component msg = ColorUtil.format(choiceText);
            if (available) {
                msg = msg.clickEvent(ClickEvent.runCommand("/wnpc dialogchoice " + i))
                        .hoverEvent(HoverEvent.showText(ColorUtil.format(
                                plugin.getConfigManager().getMessage("dialog-choice-hover"))));
            } else {
                msg = msg.hoverEvent(HoverEvent.showText(ColorUtil.format(
                        plugin.getConfigManager().getMessage("dialog-choice-locked-hover"))));
            }
            player.sendMessage(msg);
        }
    }

    private void openGui(Player player, ActiveDialog session, Dialog dialog,
                         DialogNode node, String npcText) {
        List<DialogGuiHolder.SlotChoice> mapping = new ArrayList<>();
        List<ItemStack> buttons = new ArrayList<>();
        List<Integer>   fixedSlots = new ArrayList<>();

        for (int i = 0; i < node.getChoices().size(); i++) {
            DialogChoice choice = node.getChoices().get(i);
            boolean available = choice.isAvailable(player);
            if (!available && choice.isHideIfLocked()) continue;

            String label = available ? choice.getLabel()
                    : (choice.getLockedLabel().isEmpty() ? choice.getLabel() : choice.getLockedLabel());

            var cfg = plugin.getConfigManager();
            Material material = Material.matchMaterial(choice.getMaterial().toUpperCase(Locale.ROOT));
            if (material == null) material = cfg.guiMaterial("dialog.choice-material", Material.PAPER);
            if (!available) material = cfg.guiMaterial("dialog.locked-material", Material.GRAY_DYE);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtil.format(applyVariables(player, label)));
                if (!available) {
                    meta.lore(List.of(ColorUtil.format(
                            plugin.getConfigManager().getMessage("dialog-choice-locked-hover"))));
                }
                item.setItemMeta(meta);
            }

            buttons.add(item);
            fixedSlots.add(choice.getSlot());
            mapping.add(new DialogGuiHolder.SlotChoice(-1, available ? i : -1));
        }

        int size = dialog.getGuiSize();
        int[] resolved = resolveSlots(fixedSlots, size);

        List<DialogGuiHolder.SlotChoice> finalMapping = new ArrayList<>();
        for (int i = 0; i < mapping.size(); i++) {
            finalMapping.add(new DialogGuiHolder.SlotChoice(resolved[i], mapping.get(i).choiceIndex()));
        }

        DialogGuiHolder holder = new DialogGuiHolder(session.getDialogId(), node.getId(), finalMapping);
        String title = dialog.getGuiTitle().isEmpty()
                ? plugin.getConfigManager().guiString("dialog.title", "&#BBDEFBДиалог")
                : dialog.getGuiTitle();

        Inventory inventory = Bukkit.createInventory(holder, size,
                ColorUtil.format(applyVariables(player, title)));
        holder.setInventory(inventory);

        ItemStack textItem = new ItemStack(
                plugin.getConfigManager().guiMaterial("dialog.text-material", Material.OAK_SIGN));
        ItemMeta textMeta = textItem.getItemMeta();
        if (textMeta != null) {
            textMeta.displayName(ColorUtil.format(
                    plugin.getConfigManager().guiString("dialog.text-name", "&#F5F5F0Реплика")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            int width = Math.max(10, plugin.getConfigManager().guiInt("dialog.text-wrap", 40));
            for (String line : wrap(npcText, width)) lore.add(ColorUtil.format(line));
            textMeta.lore(lore);
            textItem.setItemMeta(textMeta);
        }
        inventory.setItem(textSlot(size), textItem);

        for (int i = 0; i < buttons.size(); i++) {
            if (resolved[i] >= 0 && resolved[i] < size) inventory.setItem(resolved[i], buttons.get(i));
        }

        player.openInventory(inventory);
    }

    private int textSlot(int size) {
        return Math.max(0, Math.min(size - 1, plugin.getConfigManager().guiInt("dialog.text-slot", 4)));
    }

    private int[] resolveSlots(List<Integer> requested, int size) {
        int[] result = new int[requested.size()];
        Set<Integer> taken = new HashSet<>();
        taken.add(textSlot(size));

        for (int i = 0; i < requested.size(); i++) {
            int slot = requested.get(i);
            if (slot >= 0 && slot < size && taken.add(slot)) result[i] = slot;
            else result[i] = -1;
        }

        int cursor = Math.max(0, Math.min(size - 1,
                plugin.getConfigManager().guiInt("dialog.first-choice-slot", 9)));
        for (int i = 0; i < result.length; i++) {
            if (result[i] >= 0) continue;
            while (cursor < size && !taken.add(cursor)) cursor++;
            result[i] = cursor < size ? cursor : -1;
        }
        return result;
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() > 0 && current.length() + word.length() + 1 > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private void startTypewriter(Player player, ActiveDialog session, Dialog dialog,
                                 DialogNode node, String fullText, int renderId) {
        int[] shown = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || session.getRenderId() != renderId
                    || !activeSessions.containsKey(player.getUniqueId())) {
                task.cancel();
                return;
            }

            shown[0]++;
            if (shown[0] >= fullText.length()) {
                task.cancel();
                renderNode(player, session, dialog, node, fullText);
                return;
            }
            player.sendActionBar(ColorUtil.format(fullText.substring(0, shown[0])));
        }, node.getTypewriter(), node.getTypewriter());
    }

    private void playNodeSound(Player player, DialogNode node) {
        if (node.getSound().isEmpty()) return;

        String[] parts = node.getSound().split(" ");
        org.bukkit.Sound sound = RegistryUtil.resolveSound(parts[0]);
        if (sound == null) return;

        float volume = 1.0f, pitch = 1.0f;
        try {
            if (parts.length >= 2) volume = Float.parseFloat(parts[1]);
            if (parts.length >= 3) pitch  = Float.parseFloat(parts[2]);
        } catch (NumberFormatException ignored) {}

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void scheduleAutoAdvance(Player player, ActiveDialog session, Dialog dialog, DialogNode node) {
        if (!node.hasAutoGoto()) return;

        int renderId = session.getRenderId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (session.getRenderId() != renderId) return;
            if (activeSessions.get(player.getUniqueId()) != session) return;

            goToNode(player, session, dialog, node.getAutoGoto());
        }, node.getAutoDelay());
    }

    private void goToNode(Player player, ActiveDialog session, Dialog dialog, String targetId) {
        if (targetId == null || targetId.isEmpty() || targetId.equalsIgnoreCase("close")) {
            activeSessions.remove(player.getUniqueId());
            if (dialog.getMode() == Dialog.Mode.GUI) player.closeInventory();
            return;
        }

        String resolved = targetId;
        if (targetId.equalsIgnoreCase("back")) {
            resolved = session.popHistory();
            if (resolved == null) {
                activeSessions.remove(player.getUniqueId());
                if (dialog.getMode() == Dialog.Mode.GUI) player.closeInventory();
                return;
            }
        } else {
            session.pushHistory(session.getCurrentNodeId());
        }

        DialogNode next = dialog.getNode(resolved);
        if (next == null) {
            activeSessions.remove(player.getUniqueId());
            return;
        }

        session.setCurrentNodeId(resolved);
        session.touch();
        showNode(player, session, dialog, next);
    }

    private String applyVariables(Player player, String text) {
        if (text == null) return "";
        String result = text.replace("{player}", player.getName());

        if (result.indexOf("{flag_") < 0) return result;

        StringBuilder sb = new StringBuilder(result.length());
        int cursor = 0;
        while (true) {
            int start = result.indexOf("{flag_", cursor);
            if (start < 0) break;
            int end = result.indexOf('}', start);
            if (end < 0) break;

            String key = result.substring(start + 6, end);
            String value = plugin.getPlayerDataManager().get(player).getFlag(key);

            sb.append(result, cursor, start).append(value == null ? "" : value);
            cursor = end + 1;
        }
        sb.append(result.substring(cursor));
        return sb.toString();
    }

    public boolean hasActiveDialog(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public String getActiveDialogId(UUID playerId) {
        ActiveDialog session = activeSessions.get(playerId);
        return session == null ? null : session.getDialogId();
    }

    public String getActiveNodeId(UUID playerId) {
        ActiveDialog session = activeSessions.get(playerId);
        return session == null ? null : session.getCurrentNodeId();
    }

    public void cancelDialog(UUID playerId) {
        activeSessions.remove(playerId);
    }

    public Dialog getDialog(String id) { return dialogs.get(id); }
    public Map<String, Dialog> getDialogs() { return dialogs; }

    private static class ActiveDialog {
        private final String dialogId;
        private final UUID   npcId;
        private String currentNodeId;
        private long   lastActivity = System.currentTimeMillis();
        private final Set<String> executedNodes = new HashSet<>();

        private final java.util.Deque<String> history = new java.util.ArrayDeque<>();

        private int renderId = 0;

        ActiveDialog(String dialogId, String currentNodeId, UUID npcId) {
            this.dialogId      = dialogId;
            this.currentNodeId = currentNodeId;
            this.npcId         = npcId;
        }

        String getDialogId()      { return dialogId;      }
        String getCurrentNodeId() { return currentNodeId; }
        UUID   getNpcId()         { return npcId;         }

        void setCurrentNodeId(String id) { this.currentNodeId = id; }
        void touch() { this.lastActivity = System.currentTimeMillis(); }

        boolean isExpired(int timeoutSeconds) {
            if (timeoutSeconds <= 0) return false;
            return System.currentTimeMillis() - lastActivity > timeoutSeconds * 1_000L;
        }

        boolean wasNodeExecuted(String nodeId) {
            return executedNodes.contains(nodeId);
        }

        boolean markNodeExecuted(String nodeId) {
            return executedNodes.add(nodeId);
        }

        int  getRenderId()  { return renderId; }
        void nextRender()   { renderId++;      }

        void pushHistory(String nodeId) {
            if (nodeId == null) return;
            if (history.size() >= WoolyNpcs.getInstance().getConfigManager().getDialogHistoryLimit()) history.removeLast();
            history.push(nodeId);
        }

        String popHistory() {
            return history.isEmpty() ? null : history.pop();
        }
    }
}
