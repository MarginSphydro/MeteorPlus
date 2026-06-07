package nekiplay.meteorplus.features.hud;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import nekiplay.meteorplus.MeteorPlusAddon;

import java.util.*;
import java.util.stream.Collectors;

public class NearbyPlayersHUD extends HudElement {
	public static final HudElementInfo<NearbyPlayersHUD> INFO = new HudElementInfo<>(
		MeteorPlusAddon.HUD_GROUP,
		"nearby-players-hud",
		"显示附近玩家列表并推送 Pop/Killed 通告。",
		NearbyPlayersHUD::new
	);

	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgColors  = settings.createGroup("Colors");
	private final SettingGroup sgBG      = settings.createGroup("Background");
	private final SettingGroup sgScale   = settings.createGroup("Scale");
	private final SettingGroup sgTiming  = settings.createGroup("Timing");

	private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
		.name("range").defaultValue(50).min(1).max(500).build());
	private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
		.name("show-distance").defaultValue(true).build());
	private final Setting<Integer> distancePrecision = sgGeneral.add(new IntSetting.Builder()
		.name("distance-precision").defaultValue(1).min(0).max(3)
		.visible(showDistance::get).build());
	private final Setting<Boolean> showHealth = sgGeneral.add(new BoolSetting.Builder()
		.name("show-health").defaultValue(true).build());
	private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
		.name("shadow").defaultValue(true).build());
	private final Setting<Integer> maxPlayers = sgGeneral.add(new IntSetting.Builder()
		.name("max-players").defaultValue(5).min(1).max(50).build());

	private final Setting<Integer> popTime = sgTiming.add(new IntSetting.Builder()
		.name("pop-time").description("Pop 通告停留(秒)").defaultValue(3).min(1).max(30).build());
	private final Setting<Integer> killedTime = sgTiming.add(new IntSetting.Builder()
		.name("killed-time").description("Killed 通告停留(秒)").defaultValue(5).min(1).max(30).build());

	private final Setting<SettingColor> nameColor = sgColors.add(new ColorSetting.Builder()
		.name("name-color").defaultValue(new SettingColor(255,255,255)).build());
	private final Setting<SettingColor> bracketColor = sgColors.add(new ColorSetting.Builder()
		.name("bracket-color").defaultValue(new SettingColor(150,150,150))
		.visible(showDistance::get).build());
	private final Setting<SettingColor> distanceColor = sgColors.add(new ColorSetting.Builder()
		.name("distance-color").defaultValue(new SettingColor(200,200,200))
		.visible(showDistance::get).build());
	private final Setting<SettingColor> heartColor = sgColors.add(new ColorSetting.Builder()
		.name("heart-color").defaultValue(new SettingColor(255,0,0))
		.visible(showHealth::get).build());
	private final Setting<SettingColor> hpHighColor = sgColors.add(new ColorSetting.Builder()
		.name("hp-high-color").defaultValue(new SettingColor(0,255,0))
		.visible(showHealth::get).build());
	private final Setting<SettingColor> hpMidColor = sgColors.add(new ColorSetting.Builder()
		.name("hp-mid-color").defaultValue(new SettingColor(255,255,0))
		.visible(showHealth::get).build());
	private final Setting<SettingColor> hpLowColor = sgColors.add(new ColorSetting.Builder()
		.name("hp-low-color").defaultValue(new SettingColor(255,0,0))
		.visible(showHealth::get).build());
	private final Setting<SettingColor> popColor = sgColors.add(new ColorSetting.Builder()
		.name("pop-color").description("Pop 通告颜色").defaultValue(new SettingColor(248,255,0)).build());
	private final Setting<SettingColor> killedColor = sgColors.add(new ColorSetting.Builder()
		.name("killed-color").defaultValue(new SettingColor(255,0,0)).build());

	private final Setting<Boolean> background = sgBG.add(new BoolSetting.Builder()
		.name("background").defaultValue(true).build());
	private final Setting<SettingColor> backgroundColor = sgBG.add(new ColorSetting.Builder()
		.name("background-color").defaultValue(new SettingColor(0,0,0,100))
		.visible(background::get).build());

	private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
		.name("custom-scale").defaultValue(false).build());
	private final Setting<Double> scale = sgScale.add(new DoubleSetting.Builder()
		.name("scale").defaultValue(1.0).min(0.5).max(3.0).visible(customScale::get).build());

	private static class Notice {
		final String text;
		final long time;
		Notice(String t) { text = t; time = System.currentTimeMillis(); }
	}

	private final Map<UUID, Notice> popNotices = new LinkedHashMap<>();
	private final Map<UUID, Notice> killedNotices = new LinkedHashMap<>();
	private final Map<UUID, Integer> popCountMap = new HashMap<>();

	public NearbyPlayersHUD() {
		super(INFO);
		MeteorClient.EVENT_BUS.subscribe(this);
	}

	@EventHandler
	private void onReceivePacket(PacketEvent.Receive e) {
		if (e.packet instanceof EntityStatusS2CPacket p && p.getStatus() == 35) {
			if (p.getEntity(MeteorClient.mc.world) instanceof PlayerEntity player && player != MeteorClient.mc.player) {
				UUID id = player.getUuid();
				int cnt = popCountMap.getOrDefault(id, 0) + 1;
				popCountMap.put(id, cnt);
				double dist = MeteorClient.mc.player.distanceTo(player);
				float hp = player.getHealth() + player.getAbsorptionAmount();
				String text = String.format("%s[ %.1f] ❤%.1f WAS Popped (%d Popped)", player.getName().getString(), dist, hp, cnt);
				popNotices.put(id, new Notice(text));
			}
		}
	}

	@Override
	public void render(HudRenderer r) {
		long now = System.currentTimeMillis();
		long kp = popTime.get() * 1000L;
		long kk = killedTime.get() * 1000L;
		String df = "%." + distancePrecision.get() + "f";
		double sc = customScale.get() ? scale.get() : -1;
		double lh = r.textHeight(shadow.get(), sc) + 2;

		List<AbstractClientPlayerEntity> all = MeteorClient.mc.world.getPlayers().stream()
			.filter(p -> p != MeteorClient.mc.player && MeteorClient.mc.player.distanceTo(p) <= range.get())
			.sorted(Comparator.comparingDouble(MeteorClient.mc.player::distanceTo))
			.collect(Collectors.toList());

		Set<UUID> inRange = all.stream().map(PlayerEntity::getUuid).collect(Collectors.toSet());
		// 仅移除 popNotices 超出范围，保留 killedNotices 直至超时，以免快速 Pop 导致 kill 通告消失
		popNotices.keySet().removeIf(id -> !inRange.contains(id));
		// killedNotices.keySet().removeIf(id -> !inRange.contains(id));  // 已注释

		for (AbstractClientPlayerEntity p : all) {
			UUID id = p.getUuid();
			if (p.getHealth() + p.getAbsorptionAmount() <= 0 && !killedNotices.containsKey(id)) {
				int count = popCountMap.getOrDefault(id, 0);
				double dist = MeteorClient.mc.player.distanceTo(p);
				String text = String.format("%s[ %.1f] ❤0.0 WAS KILLED (%d Popped)", p.getName().getString(), dist, count);
				killedNotices.put(id, new Notice(text));
				popNotices.remove(id);
				popCountMap.remove(id);
			}
		}

		popNotices.entrySet().removeIf(en -> now - en.getValue().time > kp);
		killedNotices.entrySet().removeIf(en -> now - en.getValue().time > kk);

		List<AbstractClientPlayerEntity> sub = all.stream().limit(maxPlayers.get()).collect(Collectors.toList());
		boolean overflow = all.size() > maxPlayers.get();
		int more = all.size() - maxPlayers.get();

		double w = 0;
		int lines = popNotices.size() + killedNotices.size() + sub.size() + (overflow ? 1 : 0);
		for (Notice n : popNotices.values()) w = Math.max(w, r.textWidth(n.text, shadow.get(), sc));
		for (Notice n : killedNotices.values()) w = Math.max(w, r.textWidth(n.text, shadow.get(), sc));
		for (AbstractClientPlayerEntity p : sub) {
			double ww = r.textWidth(p.getName().getString(), shadow.get(), sc);
			if (showDistance.get()) {
				String num = String.format(df, MeteorClient.mc.player.distanceTo(p));
				ww += r.textWidth("[" + num + "]", shadow.get(), sc);
			}
			if (showHealth.get()) {
				String num = String.format("%.1f", p.getHealth() + p.getAbsorptionAmount());
				ww += r.textWidth(" ❤" + num, shadow.get(), sc);
			}
			w = Math.max(w, ww);
		}

		setSize(w, lines * lh);
		if (background.get()) r.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

		double yOff = 0;
		for (Notice n : popNotices.values()) {
			r.text(n.text, x, y + yOff, popColor.get(), shadow.get(), sc);
			yOff += lh;
		}
		for (Notice n : killedNotices.values()) {
			r.text(n.text, x, y + yOff, killedColor.get(), shadow.get(), sc);
			yOff += lh;
		}
		for (AbstractClientPlayerEntity p : sub) {
			double xOff = 0;
			String name = p.getName().getString();
			r.text(name, x + xOff, y + yOff, nameColor.get(), shadow.get(), sc);
			xOff += r.textWidth(name, shadow.get(), sc);
			if (showDistance.get()) {
				String num = String.format(df, MeteorClient.mc.player.distanceTo(p));
				r.text("[", x + xOff, y + yOff, bracketColor.get(), shadow.get(), sc);
				r.text(num, x + xOff + r.textWidth("[", shadow.get(), sc), y + yOff, distanceColor.get(), shadow.get(), sc);
				r.text("]", x + xOff + r.textWidth("[" + num, shadow.get(), sc), y + yOff, bracketColor.get(), shadow.get(), sc);
				xOff += r.textWidth("[" + num + "]", shadow.get(), sc);
			}
			if (showHealth.get()) {
				double hp = p.getHealth() + p.getAbsorptionAmount();
				String num = String.format("%.1f", hp);
				SettingColor c = hp >= 18 ? hpHighColor.get() : hp >= 12 ? hpMidColor.get() : hpLowColor.get();
				r.text(" ❤", x + xOff, y + yOff, heartColor.get(), shadow.get(), sc);
				xOff += r.textWidth(" ❤", shadow.get(), sc);
				r.text(num, x + xOff, y + yOff, c, shadow.get(), sc);
			}
			yOff += lh;
		}
		if (overflow) {
			String moreText = "... (+" + more + " more)";
			r.text(moreText, x, y + yOff, nameColor.get(), shadow.get(), sc);
		}
	}
}
