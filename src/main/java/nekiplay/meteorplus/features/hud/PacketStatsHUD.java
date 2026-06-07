package nekiplay.meteorplus.features.hud;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import nekiplay.meteorplus.MeteorPlusAddon;

public class PacketStatsHUD extends HudElement {
	public static final HudElementInfo<PacketStatsHUD> INFO = new HudElementInfo<>(
		MeteorPlusAddon.HUD_GROUP,
		"packet-stats-hud",
		"显示每秒发送/接收的数据包数量。",
		PacketStatsHUD::new
	);

	private final SettingGroup sg = settings.getDefaultGroup();
	private final Setting<SettingColor> labelColor = sg.add(new ColorSetting.Builder()
		.name("label-color").defaultValue(new SettingColor(255, 255, 255))
		.build()
	);
	private final Setting<SettingColor> valueColor = sg.add(new ColorSetting.Builder()
		.name("value-color").defaultValue(new SettingColor(200, 200, 200))
		.build()
	);
	private final Setting<Boolean> shadow = sg.add(new BoolSetting.Builder()
		.name("shadow").defaultValue(true).build()
	);
	private final Setting<Boolean> background = sg.add(new BoolSetting.Builder()
		.name("background").defaultValue(true).build()
	);
	private final Setting<SettingColor> backgroundColor = sg.add(new ColorSetting.Builder()
		.name("background-color").defaultValue(new SettingColor(0, 0, 0, 100))
		.visible(background::get).build()
	);

	private int sendCountRaw = 0, recvCountRaw = 0;
	private int sendPerSec = 0, recvPerSec = 0;
	private long lastTime = System.currentTimeMillis();

	public PacketStatsHUD() {
		super(INFO);
		MeteorClient.EVENT_BUS.subscribe(this);
	}

	@EventHandler
	private void onSend(PacketEvent.Send event) {
		sendCountRaw++;
	}

	@EventHandler
	private void onReceive(PacketEvent.Receive event) {
		recvCountRaw++;
	}

	@Override
	public void render(HudRenderer r) {
		// 每秒更新一次速率
		long now = System.currentTimeMillis();
		if (now - lastTime >= 1000L) {
			sendPerSec = sendCountRaw;
			recvPerSec = recvCountRaw;
			sendCountRaw = 0;
			recvCountRaw = 0;
			lastTime = now;
		}

		String label1  = "Send/s: ";
		String label2  = "Recv/s: ";
		String value1  = String.valueOf(sendPerSec);
		String value2  = String.valueOf(recvPerSec);

		double sc   = -1;
		double lh   = r.textHeight(shadow.get(), sc) + 2;
		// 先测量 label 最宽宽度，确保数字总是从相同 x 偏移处绘制
		double labelWidth = Math.max(r.textWidth(label1, shadow.get(), sc), r.textWidth(label2, shadow.get(), sc));
		// 再测量整块宽度
		double width  = labelWidth + Math.max(r.textWidth(value1, shadow.get(), sc), r.textWidth(value2, shadow.get(), sc));
		double height = lh * 2;

		setSize(width, height);
		if (background.get()) {
			r.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
		}

		// 第一行：Send/s
		double yOff = 0;
		r.text(label1, x, y + yOff, labelColor.get(), shadow.get(), sc);
		r.text(value1, x + labelWidth, y + yOff, valueColor.get(), shadow.get(), sc);

		// 第二行：Recv/s
		yOff += lh;
		r.text(label2, x, y + yOff, labelColor.get(), shadow.get(), sc);
		r.text(value2, x + labelWidth, y + yOff, valueColor.get(), shadow.get(), sc);
	}
}
