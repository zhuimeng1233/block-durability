package top.dreamfall.blockdurability;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * 专门处理 TaCZ 枪械子弹命中事件的监听器。
 * 独立成单独的类，避免在未安装 TaCZ 时引发 AmmoHitBlockEvent 的 ClassNotFound 崩溃。
 */
public class TaczHitHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (event.isCanceled()) return;
        if (!BlockHPConfig.ENABLED.get()) return;

        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (event.getHitResult() == null || event.getState() == null) return;

        BlockPos pos = event.getHitResult().getBlockPos();
        BlockState state = event.getState();

        // 不在白名单 → 什么都不做
        if (!BlockHPManager.isBreakable(state)) return;

        EntityKineticBullet bullet = event.getAmmo();
        Vec3 hitVec = event.getHitResult().getLocation();

        // --- 计算本次伤害 ---
        float damage;
        if (BlockHPConfig.USE_BULLET_DAMAGE.get() && bullet != null) {
            damage = bullet.getDamage(hitVec)
                    * BlockHPConfig.BULLET_DAMAGE_MULTIPLIER.get().floatValue();
        } else {
            damage = BlockHPConfig.DAMAGE_PER_HIT.get().floatValue();
        }
        if (!Float.isFinite(damage) || damage <= 0) return;

        // --- 造成伤害 ---
        Optional<Float> remaining = BlockHPManager.damageBlock(level, pos, state, damage);

        if (remaining.isEmpty()) {
            BlockDurability.LOGGER.info("Block broken by bullet: {} at {} with dmg {}", state.getBlock(), pos, damage);
            BlockHPEventHandler.spawnBreakEffects(serverLevel, pos, state);
        } else if (remaining.get() > 0) {
            float maxHP = BlockHPManager.getMaxHP(state);
            float ratio = maxHP > 0 ? (1f - (remaining.get() / maxHP)) : 0f;
            BlockDurability.LOGGER.debug("Block hit: {} at {}, dealt dmg: {}, HP: {}/{}", state.getBlock(), pos, damage, remaining.get(), maxHP);
            BlockHPEventHandler.spawnHitEffects(serverLevel, pos, state, hitVec, ratio);
        }
    }
}
