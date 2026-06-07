package nekiplay.meteorplus.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.util.HashMap;

public class AutoQueue extends Module {
	public static HashMap<String, String> asks = new HashMap<>(){
		{
			put("红石火把", "15");
			put("猪被闪电", "僵尸猪人");
			put("小箱子能", "27");
			put("开服年份", "2020");
			put("定位末地遗迹", "0");
			put("爬行者被闪电", "高压爬行者");
			put("大箱子能", "54");
			put("羊驼会主动", "不会");
			put("无限水", "3");
			put("挖掘速度最快", "金镐");
			put("凋灵死后", "下界之星");
			put("苦力怕的官方", "爬行者");
			put("南瓜的生长", "不需要");
			put("定位末地", "0");
		}
	};

	private final SettingGroup sgGeneral = settings.getDefaultGroup();

	public AutoQueue() {
		super(Categories.Misc, "auto-queue", "Answering questions automatically when you are queueing.");
	}

	private final Setting<Boolean> queueCheck = sgGeneral.add(new BoolSetting.Builder()
		.name("Queue Check")
		.description("Whether to check if in queue.")
		.defaultValue(true)
		.build());

	public static boolean inQueue = false;
	public void onUpdate() {
		if (mc.player == null || mc.world == null) {
			inQueue = false;
			return;
		}
		inQueue = mc.player.getInventory().contains(Items.COMPASS.getDefaultStack());
	}

	public void onDisable() {
		inQueue = false;
	}

	@EventHandler
	public void onPacketReceive(PacketEvent.Receive e) {
		if (!inQueue && queueCheck.get()) return;
		if (e.packet instanceof GameMessageS2CPacket packet) {
			for (String key : asks.keySet()) {
				if (packet.content().getString().contains(key)) {
					String[] abc = new String[]{"A", "B", "C"};
					for (String s : abc) {
						if (packet.content().getString().contains(s + "." + asks.get(key))) {
							mc.player.networkHandler.sendChatMessage(s.toLowerCase());
							return;
						}
					}
				}
			}
		}
	}
}
