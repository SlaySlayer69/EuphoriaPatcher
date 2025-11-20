# Migration Guide: Moving Files to Multi-Loader Structure

## Overview
This guide explains how to complete the migration to the multi-loader structure.
The structure is set up, but files need to be moved from the old locations to the new ones.

## Current Status
✅ Directory structure created
✅ Build scripts configured
✅ Abstract ModLoaderSpecifics created in common/
✅ FabricModLoaderSpecifics created in fabric/
✅ Fabric ClientEuphoriaPatcher created

## What Needs to Be Done

### Step 1: Move Common Code to common/

Copy all files from `src/main/java/mc/euphoria_patches/euphoria_patcher/` to `common/src/main/java/mc/euphoria_patches/euphoria_patcher/`

**EXCEPT these files (they belong elsewhere):**
- ❌ `ClientEuphoriaPatcher.java` - Already moved to fabric module
- ❌ `util/ModLoaderSpecifics.java` - Already replaced with abstract version in common
- ❌ `mixin/` directory - Mixins are Fabric-specific, move to fabric module instead

**Files to copy to common/:**
- ✅ `EuphoriaPatcher.java`
- ✅ `PatchInfo.java`
- ✅ All `features/` classes
- ✅ All `util/` classes (except the old ModLoaderSpecifics.java)
- ✅ `util/Dimensions.java` ⚠️ Note: References MinecraftClient - see Step 4

### Step 2: Move Fabric Mixins to fabric/

Copy mixin files from `src/main/java/mc/euphoria_patches/euphoria_patcher/mixin/` to `fabric/src/main/java/mc/euphoria_patches/euphoria_patcher/mixin/`

**Files to move:**
- `ClientTickMixin.java`
- `EuphoriaMixinPlugin.java`
- `EuphoriaPatcherMixin.java`
- `IrisLegacyStandardMacrosMixin.java`
- `IrisModernStandardMacrosMixin.java`
- `ReloadShadersOnDimensionChangeMixin.java`

### Step 3: Move Fabric Resources to fabric/

Copy resource files from `src/main/resources/` to `fabric/src/main/resources/`

**Files to move:**
- `fabric.mod.json` ⚠️ UPDATE entrypoint (see below)
- `euphoria_patcher.mixins.json`
- `assets/` directory
- `EuphoriaPatches_*.patch` files
- `randomUtil.json`
- `_0EuphoriaPatches_ErrorShader/` directory

**Update fabric.mod.json** - Change the entrypoint from:
```json
"main": [
    "mc.euphoria_patches.euphoria_patcher.ClientEuphoriaPatcher"
]
```
to:
```json
"main": [
    "mc.euphoria_patches.euphoria_patcher.fabric.ClientEuphoriaPatcher"
]
```

### Step 4: Update Code to Use New ModLoaderSpecifics

#### In EuphoriaPatcher.java (and any other common code):

Replace direct field access with method calls:

**OLD:**
```java
public static Path shaderpacks = ModLoaderSpecifics.shaderpacks;
public static Path configDirectory = ModLoaderSpecifics.configDirectory;
```

**NEW:**
```java
public static Path shaderpacks = ModLoaderSpecifics.shaderpacks();
public static Path configDirectory = ModLoaderSpecifics.configDirectory();
```

**OLD:**
```java
private static final boolean isDevModLoader = ModLoaderSpecifics.isDevMode;
```

**NEW:**
```java
private static final boolean isDevModLoader = ModLoaderSpecifics.isDevModeStatic();
```

#### In Dimensions.java (common code):

The Dimensions.getCurrentDimension() method currently uses MinecraftClient directly.
This needs to be refactored since MinecraftClient is loader-specific.

**Option 1: Move Dimensions.java to common and getCurrentDimension() to ModLoaderSpecifics**
The getCurrentDimension method is already in FabricModLoaderSpecifics, so Dimensions.java 
just needs to call it instead of accessing MinecraftClient directly.

**Change in Dimensions.java getCurrentDimension():**
```java
// REMOVE the MinecraftClient access code
// DELETE these lines:
MinecraftClient client = MinecraftClient.getInstance();
if (client == null || client.world == null) {
    return "overworld";
}
Identifier dimensionId = client.world.getRegistryKey().getValue();
String currentDimensionId = dimensionId.toString();

// REPLACE with a call to the loader-specific method:
// This method signature already accepts a dimension ID string
// So the caller should get the dimension ID from ModLoaderSpecifics
```

Actually, looking at the code more carefully, `Dimensions.getCurrentDimension(String)` takes a dimension ID as a parameter and does the mapping. The loader-specific code (in FabricModLoaderSpecifics) gets the current dimension ID from Minecraft and calls Dimensions.getCurrentDimension(). This is correct and Dimensions.java can stay in common!

### Step 5: Update Mixin References (if needed)

Check that mixins in fabric module can access common code. Since fabric depends on common, this should work automatically.

### Step 6: Delete Old Files (Optional)

After copying files to their new locations and verifying everything works:
- Delete the old `src/main/java` directory
- Delete the old `src/main/resources` directory
- Keep any backup if needed

### Step 7: Test the Build

```powershell
# Test building just Fabric
./gradlew :fabric:build

# If successful, test building all loaders
./gradlew buildAll
```

## Quick PowerShell Commands to Help

### Copy Java Files to Common (Windows PowerShell)
```powershell
# From the root project directory

# Copy all java files except mixins and ClientEuphoriaPatcher
$sourceBase = "src\main\java\mc\euphoria_patches\euphoria_patcher"
$destBase = "common\src\main\java\mc\euphoria_patches\euphoria_patcher"

# Copy main file
Copy-Item "$sourceBase\EuphoriaPatcher.java" -Destination "$destBase\" -Force

# Copy PatchInfo if it exists
Copy-Item "$sourceBase\PatchInfo.java" -Destination "$destBase\" -Force -ErrorAction SilentlyContinue

# Copy features directory
Copy-Item "$sourceBase\features" -Destination "$destBase\" -Recurse -Force

# Copy util directory (but not ModLoaderSpecifics.java - we already have the abstract one)
Get-ChildItem "$sourceBase\util" -File | Where-Object { $_.Name -ne "ModLoaderSpecifics.java" } | ForEach-Object {
    Copy-Item $_.FullName -Destination "$destBase\util\" -Force
}

Write-Host "Common files copied!"
```

### Copy Mixins to Fabric
```powershell
$sourceMixins = "src\main\java\mc\euphoria_patches\euphoria_patcher\mixin"
$destMixins = "fabric\src\main\java\mc\euphoria_patches\euphoria_patcher\mixin"

Copy-Item $sourceMixins -Destination "fabric\src\main\java\mc\euphoria_patches\euphoria_patcher\" -Recurse -Force

Write-Host "Mixins copied to Fabric!"
```

### Copy Resources to Fabric
```powershell
$sourceRes = "src\main\resources"
$destRes = "fabric\src\main\resources"

Copy-Item "$sourceRes\*" -Destination $destRes -Recurse -Force

Write-Host "Resources copied to Fabric!"
```

## Verification Checklist

After migration:
- [ ] Common module compiles: `./gradlew :common:build`
- [ ] Fabric module compiles: `./gradlew :fabric:build`
- [ ] fabric.mod.json has correct entrypoint: `mc.euphoria_patches.euphoria_patcher.fabric.ClientEuphoriaPatcher`
- [ ] No references to old ModLoaderSpecifics fields (should all be method calls)
- [ ] Mixins are in fabric module
- [ ] Resources are in fabric module
- [ ] Common code has no loader-specific imports (no FabricLoader, MinecraftClient in common code directly)

## Troubleshooting

### "Cannot find symbol: ModLoaderSpecifics.shaderpacks"
Change from field access to method call:
```java
// OLD: ModLoaderSpecifics.shaderpacks
// NEW: ModLoaderSpecifics.shaderpacks()
```

### "Class not found: ClientEuphoriaPatcher"
Update fabric.mod.json entrypoint to include the `.fabric` package:
```json
"mc.euphoria_patches.euphoria_patcher.fabric.ClientEuphoriaPatcher"
```

### "Cannot access MinecraftClient in common code"
This is correct! Common code should not directly access MinecraftClient. Use ModLoaderSpecifics methods instead.

### Build fails with "duplicate class" errors
Make sure you didn't copy files to both common and fabric that should only be in one place. Check the file lists above.

## Need Help?

If you get stuck, the key principle is:
- **Common**: Pure Java code with no loader-specific imports
- **Fabric**: Fabric-specific code (mixins, FabricLoader usage, MinecraftClient access)
- **ModLoaderSpecifics**: The bridge between common and loader-specific code
