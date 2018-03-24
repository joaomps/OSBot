package wineGrabber;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;

import org.osbot.rs07.api.map.Area;
import org.osbot.rs07.api.map.Position;
import org.osbot.rs07.api.map.constants.Banks;
import org.osbot.rs07.api.model.GroundItem;
import org.osbot.rs07.api.ui.Skill;
import org.osbot.rs07.api.ui.Spells;
import org.osbot.rs07.script.MethodProvider;
import org.osbot.rs07.script.Script;
import org.osbot.rs07.script.ScriptManifest;
import org.osbot.rs07.utility.ConditionalSleep;

@ScriptManifest(author = "joaomps", info = "Grabs wines of zamorak", logo = "", name = "wineGrabber", version = 1.0)

public class Main extends Script {

	public static Area area = new Area(2933, 3513, 2930, 3517);
	public static Position pos = new Position(2930, 3515, 0);
	public static Position ppos = new Position(2931, 3515, 0);
	public static String state;
	private Optional<Integer> price;
	private long startTime;
	private long itemCount = 0;
	private long currentItemCount = -1;
	public static int desiredPitch = 67;

	@ Override
	public void onStart() {
		price = getPrice(245);
		log(price);
		startTime = System.currentTimeMillis();
		getExperienceTracker().start(Skill.MAGIC);
	}


	@ Override
	public int onLoop() throws InterruptedException {
		if (getInventory().contains("Law rune")) {
			for (States state : States.values())
				if (state.canProcess(this))
					state.process(this);
			recountItems();
		} else {
			stop();
		}
		return 33;
	}


	enum States {
		GRAB {
			@ Override
			public boolean canProcess(MethodProvider mp) {
				return area.contains(mp.myPlayer()) && !mp.getInventory().isFull();
			}

			@ Override
			public void process(MethodProvider mp) throws InterruptedException {
				if (!mp.myPlayer().getPosition().equals(ppos)) {
					ppos.interact(mp.getBot(), "Walk here");
				}
				GroundItem wine = mp.getGroundItems().closest("Wine of zamorak");
				if (mp.getMagic().isSpellSelected()) {
					if (wine != null && wine.isVisible()) {
						wine.interact("Cast");
						state = "Interacting";
						sleep(random(2500, 4500));
					} else {
						Rectangle rect = pos.getPolygon(mp.getBot()).getBounds();
						mp.getMouse().move(rect.x + (rect.width / 2), rect.y + (rect.height / 2));
						if(mp.getCamera().getPitchAngle() != desiredPitch) {
							mp.getCamera().movePitch(desiredPitch);
							sleep(1250);
						}
						state = "Moving mouse to center";
					}
				} else {
					state = "Casting";
					mp.getMagic().castSpell(Spells.NormalSpells.TELEKINETIC_GRAB);
				}
			}
		},

		WALK {
			@ Override 
			public boolean canProcess(MethodProvider mp) {
				return !Banks.FALADOR_WEST.contains(mp.myPlayer()) && mp.getInventory().isFull()
						|| !area.contains(mp.myPlayer()) && !mp.getInventory().isFull();
			}

			@ Override 
			public void process(MethodProvider mp) throws InterruptedException {
				state = "Walking";
				if (mp.getInventory().isFull() && !Banks.FALADOR_WEST.contains(mp.myPlayer())) {
					mp.log("Teleporting to Falador");
					mp.getMagic().castSpell(Spells.NormalSpells.FALADOR_TELEPORT);
					sleep(random(3100, 5700));
					mp.getWalking().webWalk(Banks.FALADOR_WEST);
				} else {
					mp.getWalking().webWalk(area);
				}
			}
		},

		BANK {
			@ Override 
			public boolean canProcess(MethodProvider mp) {
				return Banks.FALADOR_WEST.contains(mp.myPlayer()) && mp.getInventory().isFull();
			}


			@ Override 
			public void process(MethodProvider mp) throws InterruptedException {
				if (mp.getBank().isOpen()) {
					state = "Depositing";
					mp.getBank().depositAll("Wine of zamorak");
				} else {
					state = "Opening bank";
					mp.getBank().open();
					new ConditionalSleep(random(3500, 7000)) {
						@Override
						public boolean condition() throws InterruptedException {
							return mp.getBank().isOpen();
						}
					}.sleep();
				}
			}
		};

		public abstract boolean canProcess(MethodProvider mp) throws InterruptedException;
		public abstract void process(MethodProvider mp) throws InterruptedException;
	}

	public void onPaint(Graphics2D g) {
		Graphics2D cursor = (Graphics2D) g.create();
		Graphics2D paint = (Graphics2D) g.create();
		final long runTime = System.currentTimeMillis() - startTime;
		Point mP = getMouse().getPosition();
		Rectangle rect = pos.getPolygon(getBot()).getBounds();
		Color tBlack = new Color(0, 0, 0, 128);
		paint.setFont(new Font("Arial", Font.PLAIN, 12));
		paint.setColor(tBlack);
		paint.fillRect(0, 255, 200, 80);
		paint.setColor(Color.WHITE);
		paint.drawRect(0, 255, 200, 80);
		paint.drawString("wineGrabber " + getVersion(), 5, 270);
		paint.drawString("Time running: " + formatTime(runTime), 5, 285);
		paint.drawString("Magic xp gained: " + formatValue(getExperienceTracker().getGainedXP(Skill.MAGIC)), 5, 300);
		paint.drawString("Gained money: " + price.get() * itemCount, 5, 315);
		paint.drawString("State: " + state, 5, 330);
		cursor.setColor(Color.WHITE);
		cursor.drawLine(mP.x - 5, mP.y + 5, mP.x + 5, mP.y - 5);
		cursor.drawLine(mP.x + 5, mP.y + 5, mP.x - 5, mP.y - 5);
		cursor.drawPolygon(pos.getPolygon(getBot()));
		cursor.drawString("x", rect.x + (rect.width / 2), rect.y + (rect.height / 2));
	}

	private Optional<Integer> getPrice(int id) {
		Optional<Integer> price = Optional.empty();
		try {
			URL url = new URL("http://api.rsbuddy.com/grandExchange?a=guidePrice&i=" + id);
			URLConnection con = url.openConnection();
			con.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
			con.setUseCaches(true);
			BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String[] data = br.readLine().replace("{", "").replace("}", "").split(",");
			br.close();
			price = Optional.of(Integer.parseInt(data[0].split(":")[1]));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return price;
	}

	public final String formatValue(final long l) {
		return (l > 1_000_000) ? String.format("%.2fm", (double) (l / 1_000_000))
				: (l > 1000) ? String.format("%.1fk", (double) (l / 1000)) : l + "";
	}


	public final String formatTime(final long ms) {
		long s = ms / 1000, m = s / 60, h = m / 60;
		s %= 60;
		m %= 60;
		h %= 24;
		return String.format("%02d:%02d:%02d", h, m, s);
	}

	public void recountItems() {
		long amt = getInventory().getAmount("Wine of zamorak");
		if (currentItemCount == -1) {
			currentItemCount = amt;
		} else if (amt < currentItemCount) {
			currentItemCount = amt;
		} else {
			itemCount += amt - currentItemCount;
			currentItemCount = amt;
		}
	}
}