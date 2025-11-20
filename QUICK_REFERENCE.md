# Quick Reference: Multi-Loader Structure

## Build Commands

```bash
# Build everything (recommended)
./gradlew buildAll

# Build only Fabric
./gradlew :fabric:build

# Clean everything
./gradlew cleanAll

# Clean and rebuild Fabric
./gradlew :fabric:clean :fabric:build
```

## Key Files Created

### Root Level
- `settings.gradle` - Defines all subprojects (common, fabric, forge, etc.)
- `build.gradle` - Orchestrates builds in correct order
- `MULTILOADER_STRUCTURE.md` - Complete documentation
- `MIGRATION_GUIDE.md` - Step-by-step migration instructions

### Common Module (common/)
- `common/src/main/java/.../util/ModLoaderSpecifics.java` - Abstract base class
- `common/build.gradle` - Common module build configuration

### Fabric Module (fabric/)
- `fabric/src/main/java/.../fabric/FabricModLoaderSpecifics.java` - Fabric implementation
- `fabric/src/main/java/.../fabric/ClientEuphoriaPatcher.java` - Fabric initializer
- `fabric/build.gradle` - Fabric-specific build with Loom, shadowing, etc.
- `fabric/gradle.properties` - Fabric versions (MC, Yarn, Loader)

## Architecture Pattern

```
┌─────────────────────────────────────────┐
│         Root build.gradle               │
│  (orchestrates all subproject builds)   │
└─────────────────────────────────────────┘
                    │
        ┌───────────┼───────────┬─────────┐
        │           │           │         │
    ┌───▼────┐  ┌──▼─────┐  ┌─▼────┐  ┌─▼────┐
    │ common │  │ fabric │  │forge │  │ etc  │
    └───┬────┘  └──┬─────┘  └─┬────┘  └─┬────┘
        │          │ depends  │         │
        │          └─────►────┘         │
        │                 │             │
        └─────────────────┴─────────────┘
              (all depend on common)
```

## ModLoaderSpecifics Pattern

```java
// 1. Common defines interface
public abstract class ModLoaderSpecifics {
    public abstract Path getShaderpacksPath();
    // ... other abstract methods
}

// 2. Fabric implements it
public class FabricModLoaderSpecifics extends ModLoaderSpecifics {
    @Override
    public Path getShaderpacksPath() {
        return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    }
}

// 3. Fabric initializer sets the instance
FabricModLoaderSpecifics impl = new FabricModLoaderSpecifics();
ModLoaderSpecifics.setInstance(impl);

// 4. Common code uses it
Path shaderpacks = ModLoaderSpecifics.shaderpacks();
```

## Directory Layout

```
EuphoriaPatcher/
├── common/              ← All shared code
│   ├── src/main/java/
│   │   └── mc/euphoria_patches/euphoria_patcher/
│   │       ├── EuphoriaPatcher.java
│   │       ├── features/
│   │       └── util/
│   └── build.gradle
│
├── fabric/              ← Fabric-specific code
│   ├── src/main/java/
│   │   └── mc/euphoria_patches/euphoria_patcher/
│   │       ├── fabric/
│   │       │   ├── ClientEuphoriaPatcher.java
│   │       │   └── FabricModLoaderSpecifics.java
│   │       └── mixin/   ← Fabric mixins
│   ├── src/main/resources/
│   │   ├── fabric.mod.json
│   │   └── euphoria_patcher.mixins.json
│   ├── build.gradle
│   └── gradle.properties
│
├── forge/               ← TODO: Add Forge
├── neoforge/            ← TODO: Add NeoForge
├── forgeLegacy/         ← TODO: Add Forge 1.8.9-1.12.2
├── forge1.7.10/         ← TODO: Add Forge 1.7.10
│
├── build.gradle         ← Root orchestrator
├── settings.gradle      ← Project definitions
└── version.properties   ← Shared version
```

## Next Steps to Complete Migration

1. **Copy files** (see MIGRATION_GUIDE.md for PowerShell commands):
   - Common Java files → `common/src/main/java/`
   - Fabric mixins → `fabric/src/main/java/.../mixin/`
   - Fabric resources → `fabric/src/main/resources/`

2. **Update fabric.mod.json** entrypoint:
   ```json
   "main": ["mc.euphoria_patches.euphoria_patcher.fabric.ClientEuphoriaPatcher"]
   ```

3. **Update EuphoriaPatcher.java**:
   - Change `ModLoaderSpecifics.shaderpacks` → `ModLoaderSpecifics.shaderpacks()`
   - Change `ModLoaderSpecifics.configDirectory` → `ModLoaderSpecifics.configDirectory()`
   - Change `ModLoaderSpecifics.isDevMode` → `ModLoaderSpecifics.isDevModeStatic()`

4. **Test build**:
   ```bash
   ./gradlew :fabric:build
   ```

## Adding Other Mod Loaders

For each new loader (forge, neoforge, etc.):

1. Create `{loader}/src/main/java/.../{loader}/` directory
2. Create `{Loader}ModLoaderSpecifics.java` extending `ModLoaderSpecifics`
3. Create loader initializer that calls `ModLoaderSpecifics.setInstance()`
4. Create `{loader}/build.gradle` with loader-specific dependencies
5. Add loader-specific resources
6. That's it! Common code automatically works.

## Common Issues & Fixes

| Issue | Fix |
|-------|-----|
| "Cannot find ModLoaderSpecifics.shaderpacks" | Use `.shaderpacks()` method instead of field |
| "Class not found: ClientEuphoriaPatcher" | Update fabric.mod.json to include `.fabric` package |
| "Duplicate class" errors | Don't copy same file to both common and fabric |
| "Cannot access MinecraftClient in common" | Use `ModLoaderSpecifics` methods, don't access MC directly |

## Documentation Files

- 📘 `MULTILOADER_STRUCTURE.md` - Complete architecture documentation
- 📗 `MIGRATION_GUIDE.md` - Detailed step-by-step migration guide
- 📙 `QUICK_REFERENCE.md` - This file (quick commands and patterns)
