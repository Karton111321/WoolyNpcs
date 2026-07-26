package ru.qweyns.woolynpcs;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.command.PluginCommand;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.qweyns.woolynpcs.command.NpcCommand;
import ru.qweyns.woolynpcs.config.ConfigManager;
import ru.qweyns.woolynpcs.data.RecycleBin;
import ru.qweyns.woolynpcs.data.StorageManager;
import ru.qweyns.woolynpcs.gui.GuiListener;
import ru.qweyns.woolynpcs.listener.NpcListener;
import ru.qweyns.woolynpcs.manager.LookAtTask;
import ru.qweyns.woolynpcs.manager.NpcManager;
import ru.qweyns.woolynpcs.manager.ProximityTask;
import ru.qweyns.woolynpcs.manager.SelectionManager;
import ru.qweyns.woolynpcs.dialog.DialogManager;
import ru.qweyns.woolynpcs.menu.MenuManager;
import ru.qweyns.woolynpcs.schedule.RoutineTask;
import ru.qweyns.woolynpcs.playerdata.PlayerDataManager;
import ru.qweyns.woolynpcs.visibility.PlayerVisibilityManager;
import ru.qweyns.woolynpcs.waypoint.WalkingTask;
import ru.qweyns.woolynpcs.waypoint.WaypointVisualizer;
import ru.qweyns.woolynpcs.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public final class WoolyNpcs extends JavaPlugin {
    private static WoolyNpcs    instance;
    private static NamespacedKey entityTag;

    private ConfigManager  configManager;
    private NpcManager     npcManager;
    private StorageManager storageManager;
    private RecycleBin     recycleBin;
    private DialogManager  dialogManager;
    private PlayerVisibilityManager playerVisibilityManager;
    private SelectionManager selectionManager;
    private MenuManager    menuManager;
    private PlayerDataManager playerDataManager;
    private NpcListener    npcListener;
    private WalkingTask    walkingTask;
    private WaypointVisualizer waypointVisualizer;

    private final List<BukkitTask> tasks = new ArrayList<>();

    private Economy economy = null;
    private boolean hasPapi = false;

    @Override
    public void onEnable() {
        instance  = this;
        entityTag = new NamespacedKey(this, "managed");

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        setupVault();
        setupPAPI();

        this.configManager  = new ConfigManager(this);
        this.npcManager     = new NpcManager(this);
        this.storageManager = new StorageManager(this);
        this.recycleBin     = new RecycleBin(this);
        this.dialogManager  = new DialogManager(this);
        this.playerDataManager       = new PlayerDataManager(this);
        this.playerVisibilityManager = new PlayerVisibilityManager(this);
        this.selectionManager        = new SelectionManager(this);
        this.menuManager             = new MenuManager(this);
        this.waypointVisualizer      = new WaypointVisualizer(this);

        cleanupManagedEntities();

        NpcCommand npcCommand = new NpcCommand(this);
        PluginCommand command = getCommand("woolynpcs");
        if (command == null) {
            getLogger().severe("Команда 'woolynpcs' не найдена в plugin.yml — плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(npcCommand);
        command.setTabCompleter(npcCommand);

        this.npcListener = new NpcListener(this);
        Bukkit.getPluginManager().registerEvents(npcListener, this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new ru.qweyns.woolynpcs.listener.TriggerListener(this), this);

        long delay = Math.max(0L, configManager.getStartupDelay());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            storageManager.loadNpcs();
            dialogManager.reload();
            menuManager.reload();
            getLogger().info("Loaded " + npcManager.getActiveNpcs().size() + " NPC(s).");
            getLogger().info("Loaded " + dialogManager.getDialogs().size() + " dialog(s).");
            getLogger().info("Loaded " + menuManager.getMenus().size() + " menu(s).");
        }, delay);

        startTasks();

        printBanner(true);
    }

    private void startTasks() {
        stopTasks();

        if (configManager.isAutosaveEnabled()) {
            long intervalTicks = Math.max(1, configManager.getAutosaveInterval()) * 60L * 20L;
            tasks.add(Bukkit.getScheduler().runTaskTimer(this,
                    storageManager::flushNow, intervalTicks, intervalTicks));
        }

        long flushInterval = configManager.getSaveFlushInterval();
        tasks.add(Bukkit.getScheduler().runTaskTimer(this,
                storageManager::flushIfDirty, flushInterval, flushInterval));

        long playerFlush = configManager.getPlayerFlushInterval();
        tasks.add(Bukkit.getScheduler().runTaskTimer(this,
                playerDataManager::flushDirty, playerFlush, playerFlush));

        tasks.add(Bukkit.getScheduler().runTaskTimer(this,
                npcManager::updatePapiHolograms, 20L, 20L));

        long lookInterval = Math.max(1, configManager.getLookUpdateInterval());
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new LookAtTask(this), lookInterval, lookInterval));

        long proximityInterval = Math.max(1, configManager.getProximityUpdateInterval());
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new ProximityTask(this), proximityInterval, proximityInterval));

        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new RoutineTask(this), 20L, 20L));

        this.walkingTask = new WalkingTask(this);
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, walkingTask, 1L, 1L));
    }

    private void stopTasks() {
        for (BukkitTask task : tasks) task.cancel();
        tasks.clear();
    }

    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            getLogger().info("Vault Economy successfully hooked!");
        }
    }

    private void setupPAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        hasPapi = true;
        getLogger().info("PlaceholderAPI successfully hooked!");
        registerExpansion();
    }

    private void registerExpansion() {
        try {
            new ru.qweyns.woolynpcs.hook.WoolyNpcsExpansion(this).register();
            getLogger().info("Плейсхолдеры %woolynpcs_...% зарегистрированы.");
        } catch (Throwable t) {
            getLogger().warning("Не удалось зарегистрировать плейсхолдеры: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        tasks.clear();
        if (storageManager != null) storageManager.saveNpcsSync();
        if (playerDataManager != null) playerDataManager.close();
        if (waypointVisualizer != null) waypointVisualizer.stopAll();
        if (npcManager     != null) npcManager.despawnAll();
        printBanner(false);
    }

    private void cleanupManagedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isManagedEntity(entity)) entity.remove();
            }
        }
    }

    public boolean isManagedEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(entityTag, PersistentDataType.BYTE);
    }

    public void tagManagedEntity(Entity entity) {
        entity.getPersistentDataContainer().set(entityTag, PersistentDataType.BYTE, (byte) 1);
    }

    public void reload() {
        storageManager.saveNpcsSync();
        npcManager.despawnAll();
        configManager.reload();
        storageManager.reload();
        dialogManager.reload();
        menuManager.reload();
        startTasks();
    }

    private void printBanner(boolean enable) {
        String accentHex  = enable ? "&#BBDEFB" : "&#FF8B94";
        String statusText = enable ? "Запуск..."  : "Выключение...";

        Component[] lines = {
                ColorUtil.format(""),
                ColorUtil.format(accentHex + "● " + accentHex + "WoolyNpcs &#F5F5F0v"
                        + getDescription().getVersion() + " &#374151• &#F5F5F0" + statusText),
                ColorUtil.format(accentHex + "| &#F5F5F0Автор:   " + accentHex + "qweyns"),
                ColorUtil.format(accentHex + "| &#F5F5F0Версия:  " + accentHex + "1.21+ &#9CA3AF(Compatible)"),
                ColorUtil.format("")
        };

        for (Component line : lines) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }

    public static WoolyNpcs     getInstance()   { return instance;       }
    public static NamespacedKey getEntityTag()  { return entityTag;      }
    public ConfigManager  getConfigManager()    { return configManager;  }
    public NpcManager     getNpcManager()       { return npcManager;     }
    public StorageManager getStorageManager()   { return storageManager; }
    public RecycleBin     getRecycleBin()       { return recycleBin;     }

    public Economy getEconomy() { return economy; }
    public boolean hasPapi()    { return hasPapi; }
    public DialogManager getDialogManager() { return dialogManager; }
    public PlayerVisibilityManager getPlayerVisibilityManager() { return playerVisibilityManager; }
    public SelectionManager getSelectionManager() { return selectionManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public NpcListener   getNpcListener() { return npcListener; }
    public WalkingTask   getWalkingTask() { return walkingTask; }
    public WaypointVisualizer getWaypointVisualizer() { return waypointVisualizer; }
}
