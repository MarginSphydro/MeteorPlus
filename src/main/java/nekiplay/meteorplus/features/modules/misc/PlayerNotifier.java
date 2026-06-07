package nekiplay.meteorplus.features.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每秒检测服务器在线玩家变化，列出新增和离开的玩家。
 * 首次激活时不会显示任何玩家变化。
 */
public class PlayerNotifier extends Module {
	private long lastTime;
	private Map<UUID, String> lastPlayers;
	private boolean firstRun;

	public PlayerNotifier() {
		super(Categories.Misc, "player-notifier", "每秒检测服务器在线玩家变化，列出新增和离开玩家。");
	}

	@Override
	public void onActivate() {
		lastTime = System.currentTimeMillis();
		firstRun = true;
		lastPlayers = new HashMap<>();
		if (mc.getNetworkHandler() != null) {
			for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
				UUID id = entry.getProfile().id();
				lastPlayers.put(id, entry.getProfile().name());
			}
		}
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
		if (mc.getNetworkHandler() == null) return;

		long currentTime = System.currentTimeMillis();
		if (currentTime - lastTime < 1000) return;

		// 构建当前玩家映射
		Map<UUID, String> currentPlayers = new HashMap<>();
		for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
			currentPlayers.put(entry.getProfile().id(), entry.getProfile().name());
		}

		// 首次运行仅初始化，不打印任何信息
		if (firstRun) {
			firstRun = false;
			lastPlayers = currentPlayers;
			lastTime = currentTime;
			return;
		}

		// 新增玩家
		for (Map.Entry<UUID, String> e : currentPlayers.entrySet()) {
			UUID id = e.getKey();
			if (!lastPlayers.containsKey(id)) {
				info(Text.of("§8[§a+§8]§7 " + e.getValue()));
			}
		}

		// 离开玩家
		for (Map.Entry<UUID, String> e : lastPlayers.entrySet()) {
			UUID id = e.getKey();
			if (!currentPlayers.containsKey(id)) {
				info(Text.of("§8[§c-§8]§7 " + e.getValue()));
			}
		}

		// 更新状态
		lastPlayers = currentPlayers;
		lastTime = currentTime;
	}

	@Override
	public void onDeactivate() {
		if (lastPlayers != null) lastPlayers.clear();
	}
}
