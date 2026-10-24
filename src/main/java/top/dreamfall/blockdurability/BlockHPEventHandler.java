package top.dreamfall.blockdurability;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * 监听 AmmoHitBlockEvent，对白名单内的方块造成耐久伤害。
 * 同时监听定时与卸载事件，管理方块耐久状态的生命周期。
 */
public class BlockHPEventHandler {

    @SubscribeEvent
    public void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (!BlockHPConfig.ENABLED.get()) return;

        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getHitResult().getBlockPos();
        BlockState state = event.getState();

        // 不在白名单 → 什么都不做，TACZ 原逻辑照常（弹孔等）
        if (!BlockHPManager.isBreakable(state)) return;

        EntityKineticBullet bullet = event.getAmmo();
        Vec3 hitVec = event.getHitResult().getLocation();

        // --- 计算本次伤害 ---
        float damage;
        if (BlockHPConfig.USE_BULLET_DAMAGE.get()) {
            damage = bullet.getDamage(hitVec)
                    * BlockHPConfig.BULLET_DAMAGE_MULTIPLIER.get().floatValue();
        } else {
            damage = BlockHPConfig.DAMAGE_PER_HIT.get().floatValue();
        }
        if (damage <= 0) return;

        // --- 造成伤害 ---
        Optional<Float> remaining = BlockHPManager.damageBlock(level, pos, state, damage);

        if (remaining.isEmpty()) {
            // 方块已破坏
            spawnBreakEffects(serverLevel, pos, state);
        } else if (remaining.get() > 0) {
            // 还在，打击反馈
            float ratio = 1f - (remaining.get() / BlockHPManager.getMaxHP(state));
            spawnHitEffects(serverLevel, pos, state, hitVec, ratio);
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            // 每 2 秒 (40 ticks) 检查一次残血方块的自愈与内存清理
            if (serverLevel.getGameTime() % 40 == 0) {
                BlockHPManager.tick(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockHPManager.onChunkUnload(serverLevel, event.getChunk().getPos());
        }
    }

    private void spawnBreakEffects(ServerLevel level, BlockPos pos, BlockState state) {
        if (BlockHPConfig.SPAWN_BREAK_PARTICLES.get()) {
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    20, 0.25, 0.25, 0.25, 0.5);
        }
        if (BlockHPConfig.PLAY_BREAK_SOUND.get()) {
            level.playSound(null, pos,
                    state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void spawnHitEffects(ServerLevel level, BlockPos pos, BlockState state,
                                  Vec3 hitVec, float damageRatio) {
        if (BlockHPConfig.SPAWN_BREAK_PARTICLES.get()) {
            int count = 1 + (int) (damageRatio * 8);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    hitVec.x, hitVec.y, hitVec.z,
                    count, 0.1, 0.1, 0.1, 0.2);
        }
        if (BlockHPConfig.PLAY_BREAK_SOUND.get()) {
            level.playSound(null, pos,
                    state.getSoundType().getHitSound(),
                    SoundSource.BLOCKS,
                    0.5F + damageRatio * 0.5F,
                    0.8F + level.random.nextFloat() * 0.4F);
        }
    }
}
