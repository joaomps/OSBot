import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.osbot.rs07.api.Widgets;
import org.osbot.rs07.api.ui.RS2Widget;
import org.osbot.rs07.api.ui.Skill;
import org.osbot.rs07.script.MethodProvider;
import org.osbot.rs07.script.Script;
import org.osbot.rs07.script.ScriptManifest;
import org.osbot.rs07.utility.ConditionalSleep;

@ScriptManifest(author = "joaomps", info = "", logo = "", name = "1-10 crafting", version = 0)
public class Main extends Script {
	private CachedWidget widget;
	private Armour armour;
	private boolean started;

	@Override
	public void onStart() throws InterruptedException {
		super.onStart();
		started = true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public int onLoop() throws InterruptedException {
		if (started) {
			if (getSkills().getStatic(Skill.CRAFTING) < 7)
				armour = Armour.LEATHER_GLOVES;
			else if (getSkills().getStatic(Skill.CRAFTING) < 9)
				armour = Armour.LEATHER_BOOTS;
			else
				armour = Armour.LEATHER_COWL;
			widget = new CachedWidget(armour.getArmour());
			if (getSkills().getStatic(Skill.CRAFTING) >= 30) {
				stop();
			} else if (canCraftArmour()) {
				if (isAmountPromptVisible()) {
					if (getKeyboard().typeString("" + MethodProvider.random(27, 99), true)) {
						new CSleep(() -> !canCraftArmour() || mustContinue(), 100_000).sleep();
					}
				} else if (widget.getWidget(getWidgets()) != null) {
					if (widget.getWidget(getWidgets()).interact(armour.getOption())) {
						new CSleep(() -> !canCraftArmour() || mustContinue() || isAmountPromptVisible(), 100_000)
								.sleep();
					}
				} else if (!isItemSelected("Needle")) {
					getInventory().interact("Use", "Needle");
				} else {
					if (getInventory().interact("Use", "Leather")) {
						new CSleep(() -> widget.getWidget(getWidgets()) != null, 5_000).sleep();
					}
				}
			} else {
				if (!getBank().isOpen())
					openBank();
				else if (getInventory().contains(i -> !i.getName().equals("Needle") && !i.getName().equals("Thread")
						&& !i.getName().equals("Leather"))) {
					getBank().depositAllExcept("Needle", "Thread", "Leather");
				} else if (!getInventory().contains("Needle")) {
					if (getBank().contains("Needle")) {
						getBank().withdraw("Needle", 1);
					} else {
						stop();
					}
				} else if (!getInventory().contains("Thread")) {
					if (getBank().contains("Thread")) {
						getBank().withdrawAllButOne("Thread");
					} else {
                                                stop();
					}
				} else if (!getInventory().contains("Leather")) {
					if (getBank().contains("Leather")) {
						getBank().withdrawAll("Leather");
					} else {
						stop();
					}
				} else {
					getBank().close();
				}
			}
		}
		return 300;
	}

	private final class CSleep extends ConditionalSleep {

		private final BooleanSupplier condition;

		public CSleep(final BooleanSupplier condition, int timeout) {
			super(timeout);
			this.condition = condition;
		}

		@Override
		public boolean condition() throws InterruptedException {
			return condition.getAsBoolean();
		}
	}

	private class CachedWidget {

		private String text;
		private RS2Widget widget;

		public CachedWidget(final String text) {
			this.text = text;
		}

		public RS2Widget getWidget(final Widgets widgets) {
			if (widget == null)
				cacheWidget(widgets);
			return widget;
		}

		private void cacheWidget(final Widgets widgets) {
			RS2Widget widget = null;
			if (text != null)
				widget = widgets.getWidgetContainingText(text);
			this.widget = widget;
		}
	}

	private enum Armour {
		LEATHER_GLOVES("Gloves", "Make All pairs of Leather gloves"), LEATHER_BOOTS("Boots",
				"Make All pairs of Leather boots"), LEATHER_COWL("Cowl", "Make All Cowls");
		private String armour, option;

		Armour(String armour, String option) {
			this.armour = armour;
			this.option = option;
		}

		private String getArmour() {
			return armour;
		}

		private String getOption() {
			return option;
		}
	}

	private boolean canCraftArmour() {
		return getInventory().contains("Thread") && getInventory().contains("Needle")
				&& getInventory().contains("Leather") && !getBank().isOpen();
	}

	private boolean isItemSelected(final String item) {
		String selectedName = getInventory().getSelectedItemName();
		return selectedName != null && selectedName.equals(item);
	}

	private boolean mustContinue() {
		return getDialogues().isPendingContinuation();
	}

	private boolean isAmountPromptVisible() {
		Optional<RS2Widget> amountWidget = getWidgets().getAll().stream()
				.filter(widg -> widg.getMessage().contains("Enter amount")).findFirst();
		return amountWidget.isPresent() && amountWidget.get().isVisible();
	}

	private void openBank() throws InterruptedException {
		if (getBank().open())
			new CSleep(() -> getBank().isOpen(), 5_000).sleep();
	}
}
 