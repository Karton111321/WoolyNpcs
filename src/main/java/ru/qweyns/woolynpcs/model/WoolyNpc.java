package ru.qweyns.woolynpcs.model;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.api.NpcDespawnEvent;
import ru.qweyns.woolynpcs.api.NpcSpawnEvent;
import ru.qweyns.woolynpcs.schedule.NpcSchedule;
import ru.qweyns.woolynpcs.util.ColorUtil;
import ru.qweyns.woolynpcs.waypoint.WaypointPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class WoolyNpc {
    private final UUID   id;
    private String name;
    private Location location;
    private String   modelId;

    private final List<String>    hologramLines = new ArrayList<>();
    private final List<NpcAction> actions       = new ArrayList<>();

    private double                    holoOffsetY    = 2.3;
    private float                     holoScale      = 1.0f;
    private boolean                   holoShadow     = false;
    private boolean                   holoBackground = true;
    private int                       holoBgAlpha    = 64;
    private Display.Billboard         holoBillboard  = Display.Billboard.CENTER;
    private TextDisplay.TextAlignment holoAlignment  = TextDisplay.TextAlignment.CENTER;
    private boolean                   holoSeeThrough = false;
    private int                       holoLineWidth  = 200;
    private float                     holoViewRange  = 64.0f;

    private boolean lookAtPlayer = false;
    private double  lookDistance = 7.0;
    private boolean smoothBody   = true;
    private double  eyeHeight    = 1.62;

    private double visibilityRange       = 0.0;
    private double proximityTriggerRange = 5.0;

    private String activeAnimation  = "idle";
    private String defaultAnimation = "idle";

    private float defaultYaw;
    private float defaultPitch;

    private ArmorStand    baseEntity;
    private ModeledEntity modeledEntity;
    private TextDisplay   hologram;

    private boolean isSpawned = false;
    private boolean isSpawning = false;
    private boolean modelRetryScheduled = false;

    private boolean manualDespawn = false;

    private float   lastAppliedYaw   = Float.NaN;
    private float   lastAppliedPitch = Float.NaN;
    private boolean lastRotationWasLookAt = false;

    private long  forcedLookUntil = 0L;
    private float forcedLookYaw;
    private float forcedLookPitch;

    private final AtomicLong totalInteractions = new AtomicLong(0);

    public enum MovementMode {
        NONE,
        FOLLOW,
        WANDER
    }

    private MovementMode movementMode  = MovementMode.NONE;
    private double       movementRange = 8.0;
    private double       movementSpeed = 0.15;

    private String dialogId = null;
    private WaypointPath waypointPath = null;
    private NpcSchedule schedule = null;
    private final List<ru.qweyns.woolynpcs.schedule.RoutineEntry> routine = new ArrayList<>();
    private long lastRoutineTime = -1;
    private long clickRewardThreshold = 0;
    private String clickRewardAction = null;
    private NpcAction clickRewardActionCache = null;
    private String requiredPermission = null;

    public WoolyNpc(UUID id, String name, Location location, String modelId) {
        this.id           = Objects.requireNonNull(id,       "id");
        this.name         = Objects.requireNonNull(name,     "name");
        this.location     = Objects.requireNonNull(location, "location").clone();
        this.defaultYaw   = this.location.getYaw();
        this.defaultPitch = this.location.getPitch();
        this.modelId      = (modelId == null) ? "" : modelId;
    }

    public void spawn() {
        if (isSpawned || isSpawning) return;
        if (location.getWorld() == null) return;
        int spawnCx = location.getBlockX() >> 4;
        int spawnCz = location.getBlockZ() >> 4;
        if (!location.getWorld().isChunkLoaded(spawnCx, spawnCz)) return;

        isSpawning = true;

        baseEntity = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        baseEntity.setInvisible(true);
        baseEntity.setInvulnerable(false);
        baseEntity.setGravity(false);
        baseEntity.setPersistent(false);
        baseEntity.setSilent(true);
        baseEntity.setCanPickupItems(false);
        baseEntity.setCollidable(false);
        baseEntity.setRemoveWhenFarAway(false);
        baseEntity.setRotation(defaultYaw, defaultPitch);
        lastAppliedYaw   = defaultYaw;
        lastAppliedPitch = defaultPitch;
        WoolyNpcs.getInstance().tagManagedEntity(baseEntity);
        WoolyNpcs.getInstance().getNpcManager().updateCache(this);

        Bukkit.getScheduler().runTaskLater(WoolyNpcs.getInstance(), () -> {
            if (baseEntity == null || !baseEntity.isValid()) {
                isSpawning = false;
                return;
            }
            isSpawned  = true;
            isSpawning = false;
            tryLoadModel(0);
            updateHologram();
            WoolyNpcs.getInstance().getNpcManager().updateCache(this);
            WoolyNpcs.getInstance().getPlayerVisibilityManager().applyHidden(this);
            Bukkit.getPluginManager().callEvent(new NpcSpawnEvent(this));
        }, 1L);
    }

    public void despawn() {
        despawn(true);
    }

    public void despawn(boolean removeEntity) {
        if (!isSpawned && !isSpawning && baseEntity == null) return;

        if (baseEntity != null) {
            WoolyNpcs.getInstance().getNpcManager().removeFromCache(baseEntity.getUniqueId());
        }

        WoolyNpcs.getInstance().getNpcManager().unregisterPapiHologram(this);

        if (modeledEntity != null) { modeledEntity.destroy(); modeledEntity = null; }

        if (removeEntity) {
            if (baseEntity != null && baseEntity.isValid()) { baseEntity.remove(); }
            if (hologram != null && hologram.isValid())     { hologram.remove();   }
        }

        baseEntity = null;
        hologram   = null;

        boolean wasSpawned  = isSpawned;
        isSpawned           = false;
        isSpawning          = false;
        modelRetryScheduled = false;
        lastAppliedYaw      = Float.NaN;
        lastAppliedPitch    = Float.NaN;

        if (wasSpawned) {
            Bukkit.getPluginManager().callEvent(new NpcDespawnEvent(this, removeEntity));
        }
    }

    private void tryLoadModel(int attempt) {
        if (baseEntity == null || !baseEntity.isValid()) {
            modelRetryScheduled = false;
            return;
        }

        int maxRetries = WoolyNpcs.getInstance().getConfigManager().getMaxModelRetries();
        if (attempt >= maxRetries) {
            modelRetryScheduled = false;
            WoolyNpcs.getInstance().getLogger().warning(
                    "Не удалось загрузить модель '" + modelId + "' для NPC '" + name
                            + "' после " + maxRetries + " попыток. Проверьте ID модели.");
            return;
        }

        if (ModelEngineAPI.getAPI().getModelRegistry().get(modelId) == null) {
            modelRetryScheduled = true;
            Bukkit.getScheduler().runTaskLater(
                    WoolyNpcs.getInstance(), () -> tryLoadModel(attempt + 1), 20L);
            return;
        }

        modelRetryScheduled = false;
        try {
            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
            if (activeModel != null) {
                ModeledEntity existing = ModelEngineAPI.getModeledEntity(baseEntity.getUniqueId());
                if (existing != null) existing.destroy();

                modeledEntity = ModelEngineAPI.createModeledEntity(baseEntity);
                modeledEntity.addModel(activeModel, true);
                playAnimation(activeAnimation);
            }
        } catch (Exception e) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "Ошибка загрузки модели '" + modelId + "' для NPC '" + name + "': " + e.getMessage());
        }
    }

    public void playAnimation(String animName) {
        if (animName == null || animName.isBlank()) return;
        this.activeAnimation = animName;
        if (modeledEntity == null) return;

        var model = modeledEntity.getModel(modelId);
        if (model.isEmpty()) return;
        AnimationHandler handler = model.get().getAnimationHandler();
        if (handler == null) return;

        if (animName.equalsIgnoreCase("none")) {
            handler.forceStopAllAnimations();
        } else {
            handler.playAnimation(animName, 0.2, 0.2, 1, true);
        }
    }

    public void setDefaultAnimation(String animName) {
        if (animName == null || animName.isBlank()) return;
        this.defaultAnimation = animName;
        playAnimation(animName);
    }

    public void restoreDefaultAnimation() {
        playAnimation(defaultAnimation);
    }

    public void updateHologram() {
        if (hologram != null && hologram.isValid()) { hologram.remove(); }
        hologram = null;

        if (hologramLines.isEmpty()) {
            WoolyNpcs.getInstance().getNpcManager().unregisterPapiHologram(this);
            return;
        }
        if (location.getWorld() == null) return;
        if (!isSpawned || baseEntity == null || !baseEntity.isValid()) return;

        Location holoLoc = baseEntity.getLocation().clone();
        if (holoLoc.getWorld() == null) return;

        hologram = (TextDisplay) holoLoc.getWorld().spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
        hologram.setPersistent(false);
        hologram.setBillboard(holoBillboard);
        hologram.setAlignment(holoAlignment);
        hologram.setShadowed(holoShadow);
        hologram.setSeeThrough(holoSeeThrough);
        hologram.setLineWidth(holoLineWidth);
        hologram.setViewRange(holoViewRange);
        hologram.setBackgroundColor(
                holoBackground
                        ? Color.fromARGB(Math.max(0, Math.min(255, holoBgAlpha)), 0, 0, 0)
                        : Color.fromARGB(0, 0, 0, 0));
        WoolyNpcs.getInstance().tagManagedEntity(hologram);

        Transformation transform = hologram.getTransformation();
        transform.getScale().set(holoScale, holoScale, holoScale);
        transform.getTranslation().set(0.0f, (float) holoOffsetY, 0.0f);
        hologram.setTransformation(transform);

        String rawText = String.join("\n", hologramLines);
        hologram.text(ColorUtil.format(rawText));

        baseEntity.addPassenger(hologram);

        if (WoolyNpcs.getInstance().hasPapi() && rawText.contains("%")) {
            WoolyNpcs.getInstance().getNpcManager().registerPapiHologram(this);
        } else {
            WoolyNpcs.getInstance().getNpcManager().unregisterPapiHologram(this);
        }
    }

    private boolean rotationUnchanged(float yaw, float pitch, boolean lookAt) {
        return lastRotationWasLookAt == lookAt
                && Float.compare(lastAppliedYaw, yaw) == 0
                && Float.compare(lastAppliedPitch, pitch) == 0;
    }

    private void rememberRotation(float yaw, float pitch, boolean lookAt) {
        lastAppliedYaw        = yaw;
        lastAppliedPitch      = pitch;
        lastRotationWasLookAt = lookAt;
    }

    public void forceLook(float yaw, float pitch, int seconds) {
        this.forcedLookYaw   = yaw;
        this.forcedLookPitch = pitch;
        this.forcedLookUntil = System.currentTimeMillis() + seconds * 1_000L;
    }

    public void clearForcedLook() {
        this.forcedLookUntil = 0L;
    }

    public boolean hasForcedLook() {
        return forcedLookUntil > System.currentTimeMillis();
    }

    public float getForcedLookYaw()   { return forcedLookYaw;   }
    public float getForcedLookPitch() { return forcedLookPitch; }

    public void rotateSmoothly(float targetYaw, float targetPitch) {
        if (baseEntity == null || !baseEntity.isValid()) return;
        if (rotationUnchanged(targetYaw, targetPitch, false)) return;
        rememberRotation(targetYaw, targetPitch, false);
        baseEntity.setRotation(targetYaw, targetPitch);
        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(targetYaw);
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
        }
    }

    public void rotateLookAt(float targetYaw, float targetPitch) {
        if (baseEntity == null || !baseEntity.isValid()) return;
        if (rotationUnchanged(targetYaw, targetPitch, true)) return;
        rememberRotation(targetYaw, targetPitch, true);

        float bodyYaw = smoothBody ? targetYaw : defaultYaw;
        baseEntity.setRotation(bodyYaw, targetPitch);
        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(bodyYaw);
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
        }
    }

    public void teleport(Location loc) {
        if (loc == null) return;
        boolean wasSpawned = isSpawned;
        if (wasSpawned) despawn(true);

        setLocation(loc);

        if (wasSpawned) spawn();
    }

    public void setModelId(String newModelId) {
        if (newModelId == null || newModelId.isBlank()) return;
        this.modelId             = newModelId;
        this.modelRetryScheduled = false;
        if (modeledEntity != null) { modeledEntity.destroy(); modeledEntity = null; }
        if (isSpawned) tryLoadModel(0);
    }

    public void setName(String newName) {
        if (newName == null || newName.isBlank()) return;
        this.name = newName;
    }

    public void setLocation(Location loc) {
        if (loc == null) return;
        this.location     = loc.clone();
        this.defaultYaw   = this.location.getYaw();
        this.defaultPitch = this.location.getPitch();
    }

    public void updateDefaultRotation(float yaw, float pitch) {
        this.defaultYaw   = yaw;
        this.defaultPitch = pitch;
    }

    public boolean hasProximityActions() {
        return actions.stream().anyMatch(a ->
                a.getClickType() == ClickType.ON_ENTER || a.getClickType() == ClickType.ON_LEAVE);
    }

    public void recordInteraction() {
        totalInteractions.incrementAndGet();
    }

    public long getTotalInteractions() {
        return totalInteractions.get();
    }

    public void setTotalInteractions(long val) {
        totalInteractions.set(val);
    }

    public void resetInteractions() {
        totalInteractions.set(0);
    }

    public UUID            getId()              { return id;              }
    public String          getName()            { return name;            }
    public Location        getLocation()        { return location.clone();}
    public String          getModelId()         { return modelId;         }
    public List<String>    getHologramLines()   { return hologramLines;   }
    public List<NpcAction> getActions()         { return actions;         }
    public ArmorStand      getBaseEntity()      { return baseEntity;      }
    public TextDisplay     getHologram()        { return hologram;        }
    public boolean         isSpawned()          { return isSpawned;       }
    public String          getActiveAnimation() { return activeAnimation; }
    public String          getDefaultAnimation(){ return defaultAnimation;}
    public float           getDefaultYaw()      { return defaultYaw;      }
    public float           getDefaultPitch()    { return defaultPitch;    }
    public ModeledEntity   getModeledEntity()   { return modeledEntity;   }

    public double                    getHoloOffsetY()    { return holoOffsetY;    }
    public float                     getHoloScale()      { return holoScale;      }
    public boolean                   isHoloShadow()      { return holoShadow;     }
    public boolean                   isHoloBackground()  { return holoBackground; }
    public int                       getHoloBgAlpha()    { return holoBgAlpha;    }
    public Display.Billboard         getHoloBillboard()  { return holoBillboard;  }
    public TextDisplay.TextAlignment getHoloAlignment()  { return holoAlignment;  }
    public boolean                   isHoloSeeThrough()  { return holoSeeThrough; }
    public int                       getHoloLineWidth()  { return holoLineWidth;  }
    public float                     getHoloViewRange()  { return holoViewRange;  }

    public void setHoloOffsetY(double v)                      { this.holoOffsetY    = v;                               }
    public void setHoloScale(float v)                         { this.holoScale      = Math.max(0.01f, v);              }
    public void setHoloShadow(boolean v)                      { this.holoShadow     = v;                               }
    public void setHoloBackground(boolean v)                  { this.holoBackground = v;                               }
    public void setHoloBgAlpha(int v)                         { this.holoBgAlpha    = Math.max(0, Math.min(255, v));   }
    public void setHoloBillboard(Display.Billboard v)         { this.holoBillboard  = v;                               }
    public void setHoloAlignment(TextDisplay.TextAlignment v) { this.holoAlignment  = v;                               }
    public void setHoloSeeThrough(boolean v)                  { this.holoSeeThrough = v;                               }
    public void setHoloLineWidth(int v)                       { this.holoLineWidth  = Math.max(1, v);                  }
    public void setHoloViewRange(float v)                     { this.holoViewRange  = Math.max(0.0f, v);               }

    public boolean isLookAtPlayer()  { return lookAtPlayer;  }
    public double  getLookDistance() { return lookDistance;  }
    public boolean isSmoothBody()    { return smoothBody;    }
    public double  getEyeHeight()    { return eyeHeight;     }

    public void setLookAtPlayer(boolean v) { this.lookAtPlayer = v; }
    public void setLookDistance(double v)  { this.lookDistance = Math.max(0, v); }
    public void setSmoothBody(boolean v)   { this.smoothBody   = v; }
    public void setEyeHeight(double v)     { this.eyeHeight    = Math.max(0, v); }

    public double getVisibilityRange()       { return visibilityRange;       }
    public double getProximityTriggerRange() { return proximityTriggerRange; }

    public void setVisibilityRange(double v)       { this.visibilityRange       = Math.max(0, v); }
    public void setProximityTriggerRange(double v) { this.proximityTriggerRange = Math.max(0, v); }

    public String        getDialogId()             { return dialogId;             }
    public void          setDialogId(String v)     { this.dialogId = v;           }
    public WaypointPath  getWaypointPath()         { return waypointPath;         }
    public void          setWaypointPath(WaypointPath v) { this.waypointPath = v; }
    public List<ru.qweyns.woolynpcs.schedule.RoutineEntry> getRoutine() { return routine; }

    public long getLastRoutineTime()          { return lastRoutineTime; }
    public void setLastRoutineTime(long v)    { this.lastRoutineTime = v; }

    public NpcSchedule   getSchedule()             { return schedule;             }
    public void          setSchedule(NpcSchedule v){ this.schedule = v;           }

    public long   getClickRewardThreshold()        { return clickRewardThreshold;  }
    public String getClickRewardAction()           { return clickRewardAction;     }
    public void   setClickRewardThreshold(long v)  { this.clickRewardThreshold = v;}

    public void setClickRewardAction(String v) {
        this.clickRewardAction      = v;
        this.clickRewardActionCache = null;
    }

    public NpcAction getClickRewardActionInstance() {
        if (clickRewardAction == null || clickRewardAction.isBlank()) return null;
        if (clickRewardActionCache != null) return clickRewardActionCache;

        String[] parts = clickRewardAction.split(":", 2);
        if (parts.length < 2) return null;
        try {
            ActionType type = ActionType.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
            clickRewardActionCache = new NpcAction(ClickType.ANY, type, parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return clickRewardActionCache;
    }

    public MovementMode getMovementMode()          { return movementMode;  }
    public void setMovementMode(MovementMode v)    { if (v != null) this.movementMode = v; }

    public double getMovementRange()               { return movementRange; }
    public void   setMovementRange(double v)       { this.movementRange = Math.max(1.0, Math.min(64.0, v)); }

    public double getMovementSpeed()               { return movementSpeed; }
    public void   setMovementSpeed(double v)       { this.movementSpeed = Math.max(0.01, Math.min(1.0, v)); }

    public boolean isManualDespawn()          { return manualDespawn;     }
    public void    setManualDespawn(boolean v){ this.manualDespawn = v;   }

    public String getRequiredPermission()          { return requiredPermission;    }
    public void   setRequiredPermission(String v)  { this.requiredPermission = v;  }
}
