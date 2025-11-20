# EuphoriaPatcher Multi-Loader Project Structure

This project has been restructured to support multiple mod loaders (Fabric, Forge, NeoForge, etc.) with a common codebase.

## Project Structure

```
EuphoriaPatcher/
├── common/                      # Common code shared across all mod loaders
│   ├── src/main/java/
│   │   └── mc/euphoria_patches/euphoria_patcher/
│   │       ├── EuphoriaPatcher.java          # Main patcher logic
│   │       ├── features/                      # All feature implementations
│   │       ├── util/
│   │       │   ├── ModLoaderSpecifics.java   # Abstract class for loader-specific code
│   │       │   └── ...                        # All other utility classes
│   │       └── ...                            # All other common classes
│   └── build.gradle                           # Common module build script
│
├── fabric/                      # Fabric-specific code
│   ├── src/main/java/
│   │   └── mc/euphoria_patches/euphoria_patcher/fabric/
│   │       ├── ClientEuphoriaPatcher.java    # Fabric mod initializer
│   │       └── FabricModLoaderSpecifics.java # Fabric implementation
│   ├── src/main/resources/
│   │   ├── fabric.mod.json                    # Fabric mod metadata
│   │   └── euphoria_patcher.mixins.json      # Mixin configuration
│   ├── build.gradle                           # Fabric build script
│   └── gradle.properties                      # Fabric-specific properties
│
├── forge/                       # Forge (1.13+) code (to be implemented)
├── neoforge/                    # NeoForge (1.20.5+) code (to be implemented)
├── forgeLegacy/                 # Forge (1.8.9-1.12.2) code (to be implemented)
├── forge1.7.10/                 # Forge 1.7.10 code (to be implemented)
│
├── build.gradle                 # Root build script - orchestrates all builds
├── settings.gradle              # Multi-project configuration
├── version.properties           # Shared version information
└── gradle.properties            # Root properties
```

## Building

### Build All Loaders (in order)
```bash
./gradlew buildAll
```
This builds all mod loader variants in the correct order:
1. forge1.7.10
2. forgeLegacy
3. neoforge
4. forge
5. fabric

### Build Specific Loader
```bash
./gradlew :fabric:build
./gradlew :forge:build
./gradlew :neoforge:build
./gradlew :forgeLegacy:build
./gradlew :forge1.7.10:build
```

### Clean All
```bash
./gradlew cleanAll
```

## How It Works

### Common Module
The `common` module contains all code that is shared across mod loaders:
- Core patcher logic (`EuphoriaPatcher.java`)
- All utilities and features
- Abstract `ModLoaderSpecifics` class that defines the interface for loader-specific operations

### Loader-Specific Modules
Each loader module (fabric, forge, etc.) contains:
- An implementation of `ModLoaderSpecifics` (e.g., `FabricModLoaderSpecifics`)
- The mod initializer that sets up the loader-specific instance
- Loader-specific resources (mixins for Fabric/NeoForge, coremods for Forge, etc.)
- A `build.gradle` with loader-specific dependencies and build configuration

### ModLoaderSpecifics Pattern
The `ModLoaderSpecifics` class uses the Strategy pattern:

1. **Common Module**: Defines abstract `ModLoaderSpecifics` with abstract methods:
   - `getShaderpacksPath()`
   - `getConfigDirectory()`
   - `isDevMode()`
   - `serverCheck()`
   - `getCurrentDimension()`

2. **Loader Modules**: Implement the abstract class with loader-specific APIs:
   ```java
   // In fabric module
   public class FabricModLoaderSpecifics extends ModLoaderSpecifics {
       @Override
       public Path getShaderpacksPath() {
           return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
       }
       // ... other implementations
   }
   ```

3. **Initialization**: Each loader's initializer sets the instance:
   ```java
   // In ClientEuphoriaPatcher.java (Fabric)
   FabricModLoaderSpecifics fabricSpecifics = new FabricModLoaderSpecifics();
   ModLoaderSpecifics.setInstance(fabricSpecifics);
   new EuphoriaPatcher(); // Common code can now use the loader-specific implementation
   ```

4. **Usage**: Common code accesses loader-specific functionality:
   ```java
   // In common code
   Path shaderpacks = ModLoaderSpecifics.getInstance().getShaderpacksPath();
   // Or use the static convenience methods:
   Path shaderpacks = ModLoaderSpecifics.shaderpacks();
   ```

## Migration Status

### ✅ Completed
- [x] Root build structure (multi-project Gradle)
- [x] Common module setup with abstract ModLoaderSpecifics
- [x] Fabric module structure and build script
- [x] Fabric ModLoaderSpecifics implementation
- [x] Fabric initializer

### 🚧 In Progress / TODO
- [ ] Implement Forge module
- [ ] Implement NeoForge module
- [ ] Implement ForgeLegacy module
- [ ] Implement Forge1.7.10 module

## Adding New Mod Loaders

To add support for a new mod loader (e.g., Forge):

1. Create the module directory structure:
   ```
   forge/
   ├── src/main/java/mc/euphoria_patches/euphoria_patcher/forge/
   ├── src/main/resources/
   ├── build.gradle
   └── gradle.properties
   ```

2. Create a ForgeModLoaderSpecifics class extending ModLoaderSpecifics

3. Create a mod initializer that:
   - Creates a ForgeModLoaderSpecifics instance
   - Calls `ModLoaderSpecifics.setInstance()`
   - Creates the EuphoriaPatcher instance

4. Add Forge-specific build configuration to `forge/build.gradle`

5. Add any Forge-specific resources (coremods, mods.toml, etc.)

6. The common code will automatically work with the new loader!

## Benefits

- **Single Codebase**: Write core logic once, reuse across all loaders
- **Easier Maintenance**: Bug fixes in common code benefit all loaders
- **Organized Structure**: Clear separation between common and loader-specific code
- **Scalable**: Easy to add support for new mod loaders
- **Consistent Builds**: Centralized build orchestration ensures all variants are built correctly
