package nekiplay.meteorplus.features.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

public class AutoSakura extends Module {
	private final SettingGroup sg = settings.getDefaultGroup();

	private final Setting<Double> range = sg.add(new DoubleSetting.Builder()
		.name("range").description("Search Range").defaultValue(4.5).min(0).sliderMax(8).build());
	private final Setting<Integer> delay = sg.add(new IntSetting.Builder()
		.name("delay").description("Delay").defaultValue(10).min(0).sliderMax(40).build());
	private final Setting<Boolean> useBonemeal = sg.add(new BoolSetting.Builder()
		.name("use-bonemeal").description("awa").defaultValue(true).build());
	private final Setting<Integer> bonemealTimeout = sg.add(new IntSetting.Builder()
		.name("bonemeal-timeout").description("owo").defaultValue(10).min(1).sliderMax(20).build());
	private final Setting<Boolean> silentSwitch = sg.add(new BoolSetting.Builder()
		.name("silent-switch").description("QAQ").defaultValue(true).build());

	private final Setting<Integer> switchDelay = sg.add(new IntSetting.Builder()
		.name("switch-delay").description("物品切换延迟(ticks)").defaultValue(5).min(0).sliderMax(40).build());
	private final Setting<Integer> bonemealDelay = sg.add(new IntSetting.Builder()
		.name("bonemeal-delay").description("骨粉使用延迟(ticks)").defaultValue(10).min(0).sliderMax(40).build());
	private final Setting<Integer> placeDelay = sg.add(new IntSetting.Builder()
		.name("place-delay").description("放置树苗延迟(ticks)").defaultValue(10).min(0).sliderMax(40).build());
	private final Setting<Boolean> rotation = sg.add(new BoolSetting.Builder()
		.name("rotation").description("启用与Self Web一致的转头逻辑").defaultValue(true).build());

	private int placeTimer;
	private int switchTimer;
	private int bonemealTimer;
	private BlockPos boneTarget;
	private int boneAttempts;
	private final Set<BlockPos> processedSaplings = new HashSet<>();
	private int pendingSlotRestore = -1; // 用于非静默模式下的物品栏恢复

	public AutoSakura() {
		super(Categories.Combat, "auto-sakura", "Sakura Bloom :3");
	}

	@Override
	public void onActivate() {
		placeTimer = 0;
		switchTimer = 0;
		bonemealTimer = 0;
		boneTarget = null;
		boneAttempts = 0;
		processedSaplings.clear();
		pendingSlotRestore = -1;
	}

	@EventHandler
	private void onTick(TickEvent.Pre event) {
		if (mc.player == null || mc.world == null) return;

		// 骨粉队列处理
		if (useBonemeal.get() && boneTarget != null) {
			// 若超时或已长成，则标记并清空
			if (bonemealTimer > 0) {
				bonemealTimer--;
				return;
			}
			if (!(mc.world.getBlockState(boneTarget).getBlock() instanceof SaplingBlock)
				|| boneAttempts >= bonemealTimeout.get()) {
				processedSaplings.add(boneTarget);
				boneTarget = null;
				boneAttempts = 0;
			} else if (bonemealTimer <= 0) {
				int boneSlot = findSlot(i -> mc.player.getInventory().getStack(i).getItem() == Items.BONE_MEAL);
				if (boneSlot != -1) {
					click(boneTarget, boneSlot);
					boneAttempts++;
					placeTimer = placeDelay.get();
					return;
				}
			}
		}

		// 处理非静默模式下的物品栏恢复
		if (pendingSlotRestore != -1) {
			if (switchTimer <= 0) {
				mc.player.getInventory().setSelectedSlot(pendingSlotRestore);
				pendingSlotRestore = -1;
			} else {
				switchTimer--;
			}
			return;
		}

// 延迟倒计时
		if (placeTimer > 0) placeTimer--;
		if (switchTimer > 0) switchTimer--;
		if (bonemealTimer > 0) bonemealTimer--;

		if (placeTimer > 0 || switchTimer > 0 || bonemealTimer > 0) return;

		// 立体扫描种植或检测已有树苗
		if (placeTimer > 0) return;
		Vec3d p = mc.player.getEntityPos();
		int r = (int) Math.ceil(range.get());
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos base = new BlockPos((int)(p.x+dx), (int)(p.y+dy-1), (int)(p.z+dz));
					if (!base.isWithinDistance(mc.player.getBlockPos(), range.get())) continue;
					BlockPos above = base.up();

					// 优先处理已有未曾催熟的树苗
					if (useBonemeal.get()
						&& mc.world.getBlockState(base.up()).getBlock() instanceof SaplingBlock
						&& !processedSaplings.contains(above)) {
						boneTarget = above;
						boneAttempts = 0;
						return;
					}

					// 种植位置：下方为泥土/草，且上方为空
					if ((mc.world.getBlockState(base).getBlock() == Blocks.DIRT
						|| mc.world.getBlockState(base).getBlock() == Blocks.GRASS_BLOCK)
						&& mc.world.getBlockState(above).isAir()) {
						int saplingSlot = findSlot(i -> {
							if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem bi)
								return bi.getBlock() instanceof SaplingBlock;
							return false;
						});
						if (saplingSlot != -1) {
							// 确保点击泥土/草方块的顶部
							click(base, saplingSlot);
							processedSaplings.add(above);
							boneTarget = above;
							boneAttempts = 0;
							bonemealTimer = bonemealDelay.get();
							return;
						}
					}
				}
			}
		}
	}

	private void click(BlockPos pos, int slot) {
		// 计算目标位置为方块顶部
		Vec3d ctr = Vec3d.ofCenter(pos).add(0, 0.5, 0);
		if (rotation.get()) {
			// Self Web风格转头逻辑
			Vec3d eyes = mc.player.getCameraPosVec(1f);
			double dx = ctr.x-eyes.x, dy=ctr.y-eyes.y, dz=ctr.z-eyes.z;
			double dist=Math.sqrt(dx*dx+dz*dz);
			float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90f;
			float pitch=(float)-Math.toDegrees(Math.atan2(dy,dist));
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw,pitch,mc.player.isOnGround(),mc.player.horizontalCollision));
		}

		if (silentSwitch.get()) {
			InvUtils.swap(slot,true);
			mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(ctr,Direction.UP,pos,false),0));
			mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
			InvUtils.swapBack();
		} else {
			int prev = mc.player.getInventory().getSelectedSlot();
			mc.player.getInventory().setSelectedSlot(slot);
			mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(ctr,Direction.UP,pos,false),0));
			mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
			// 设置延迟恢复物品栏
			pendingSlotRestore = prev;
			switchTimer = switchDelay.get();
		}
	}

	private int findSlot(IntPredicate p) {
		for (int i=0;i<9;i++) if(p.test(i)) return i;
		return -1;
	}
}
