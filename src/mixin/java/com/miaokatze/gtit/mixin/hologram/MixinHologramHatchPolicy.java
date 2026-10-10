package com.miaokatze.gtit.mixin.hologram;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtit.hologram.HologramHatchPolicy;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.util.HatchElementBuilder;

@Mixin(value = HatchElementBuilder.class, remap = false)
public abstract class MixinHologramHatchPolicy {

    @Inject(method = "atLeast(Ljava/util/Map;)Lgregtech/api/util/HatchElementBuilder;", at = @At("RETURN"))
    private void gtit$atLeast(Map<IHatchElement<?>, ? extends Number> elements,
        CallbackInfoReturnable<HatchElementBuilder<?>> callback) {
        HologramHatchPolicy.declare(this, elements.keySet(), true);
    }

    @Inject(
        method = "anyOf([Lgregtech/api/interfaces/IHatchElement;)Lgregtech/api/util/HatchElementBuilder;",
        at = @At("RETURN"))
    private void gtit$anyOf(IHatchElement<?>[] elements, CallbackInfoReturnable<HatchElementBuilder<?>> callback) {
        HologramHatchPolicy.declare(this, Arrays.asList(elements), false);
    }

    @Inject(
        method = { "hatchItemFilter(Ljava/util/function/Function;)Lgregtech/api/util/HatchElementBuilder;",
            "hatchItemFilter(Ljava/util/function/BiFunction;)Lgregtech/api/util/HatchElementBuilder;",
            "shouldReject(Ljava/util/function/Predicate;)Lgregtech/api/util/HatchElementBuilder;" },
        at = @At("RETURN"))
    private void gtit$reset(CallbackInfoReturnable<HatchElementBuilder<?>> callback) {
        HologramHatchPolicy.reset(this);
    }

    @Inject(
        method = "hatchItemFilterAnd(Ljava/util/function/Function;)Lgregtech/api/util/HatchElementBuilder;",
        at = @At("RETURN"))
    private void gtit$and(Function<?, ?> filter, CallbackInfoReturnable<HatchElementBuilder<?>> callback) {
        HologramHatchPolicy.andFunction(this, filter);
    }

    @Inject(
        method = "hatchItemFilterAnd(Ljava/util/function/BiFunction;)Lgregtech/api/util/HatchElementBuilder;",
        at = @At("RETURN"))
    private void gtit$andSignal(BiFunction<?, ?, ?> filter, CallbackInfoReturnable<HatchElementBuilder<?>> callback) {
        HologramHatchPolicy.andBiFunction(this, filter);
    }

    @Inject(method = "build()Lcom/gtnewhorizon/structurelib/structure/IStructureElement;", at = @At("RETURN"))
    private void gtit$built(CallbackInfoReturnable<IStructureElement<?>> callback) {
        HologramHatchPolicy.built(this, callback.getReturnValue());
    }
}
