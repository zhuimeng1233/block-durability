package top.dreamfall.blockdurability.mixin;

import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.util.block.BlockRayTrace;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.dreamfall.blockdurability.BlockHPConfig;

import java.util.function.Predicate;

/**
 * 劫持 TACZ 的 BlockRayTrace.IGNORES 谓词，
 * 将栅栏和栅栏门从子弹忽略列表中移除。
 * 可通过配置文件 tacz-common.toml 中的 fixBulletIgnore 关闭。
 */
@Mixin(value = BlockRayTrace.class, remap = false)
public class MixinBlockRayTrace {

    @Shadow
    @Final
    @Mutable
    private static Predicate<BlockState> IGNORES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void modifyBulletIgnore(CallbackInfo ci) {
        Predicate<BlockState> original = IGNORES;

        IGNORES = state -> {
            if (state == null) return true;
            if (original.test(state)) {
                // 配置开关：默认开启，可关闭
                if (BlockHPConfig.FIX_BULLET_IGNORE.get()) {
                    if (state.is(BlockTags.FENCES)) return false;
                    if (state.is(BlockTags.FENCE_GATES)) return false;
                }
                return true;
            }
            return false;
        };
    }
}
