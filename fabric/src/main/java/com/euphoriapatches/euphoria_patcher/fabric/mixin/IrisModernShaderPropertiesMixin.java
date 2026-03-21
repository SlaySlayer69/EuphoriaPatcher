package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Debug(export = true)
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_SHADER_PROPERTIES_CLASS, remap = false)
public class IrisModernShaderPropertiesMixin {

    @Unique
    private final Map<String, List<String>> euphoriaPatcher$profiles2 = new LinkedHashMap<>();

    @Shadow(remap = false)
    private Map<String, List<String>> profiles;

    @Inject(method = "<init>(Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/option/ShaderPackOptions;Ljava/lang/Iterable;)V", at = @At("RETURN"), remap = false, require = 0)
    private void euphoriaPatcher$splitSecondProfiles(CallbackInfo ci) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }

        int moved = 0;
        Iterator<Map.Entry<String, List<String>>> iterator = profiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<String>> entry = iterator.next();
            String key = entry.getKey();

            if (key == null || !key.startsWith("2.")) {
                continue;
            }

            String strippedKey = key.substring(2);
            List<String> values = entry.getValue();
            List<String> rewrittenValues = new ArrayList<>(values.size());
            for (String token : values) {
                if (token != null && token.startsWith("profile.2.")) {
                    rewrittenValues.add("profile." + token.substring("profile.2.".length()));
                } else {
                    rewrittenValues.add(token);
                }
            }

            euphoriaPatcher$profiles2.put(strippedKey, rewrittenValues);
            iterator.remove();
            moved++;
        }

        if (moved > 0) {
            euphoriaPatcher$debugLog("Split secondary profiles from base map: moved=" + moved + ", profiles2=" + euphoriaPatcher$profiles2.size());
        }
    }

    @Unique
    public Map<String, List<String>> euphoriaPatcher$getProfiles2() {
        return euphoriaPatcher$profiles2;
    }

    @Unique
    private void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisModernShaderPropertiesMixin] " + message);
    }
}

