package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

// Backend-agnostic sink for uniform declarations
public interface UniformDeclarer {
    void uniform1b(String name, BooleanSupplier value);

    void uniform1i(String name, IntSupplier value);

    void uniform1f(String name, DoubleSupplier value);

    void uniform2f(String name, DoubleSupplier x, DoubleSupplier y);

    void uniform2i(String name, IntSupplier x, IntSupplier y);

    void uniform3f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z);

    void uniform3i(String name, IntSupplier x, IntSupplier y, IntSupplier z);

    void uniform3d(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z);

    void uniform4f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w);
}
