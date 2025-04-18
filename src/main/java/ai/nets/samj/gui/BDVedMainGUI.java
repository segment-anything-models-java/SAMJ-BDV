package ai.nets.samj.gui;

import ai.nets.samj.annotation.Mask;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.gui.components.ComboBoxItem;
import ai.nets.samj.gui.tools.Tools;
import ai.nets.samj.ui.ConsumerInterface;
import ai.nets.samj.util.MultiPromptsWithScript;
import bdv.interactive.prompts.BdvPrompts;
import bdv.interactive.prompts.BdvPromptsUtils;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.miginfocom.layout.CC;
import net.miginfocom.swing.MigLayout;

import org.scijava.Context;
import org.scijava.LocalDetachedContext;
import org.scijava.module.ModuleService;
import org.scijava.script.ScriptService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class BDVedMainGUI <OT extends RealType<OT> & NativeType<OT>> extends MainGUI {
	public BDVedMainGUI(final BdvPrompts<?,OT> samjBdv, final String bdvWindowTitle) {
		super(emptyFakeConsumer);
		annotator = samjBdv;
		defaultBgColor = scriptPathElem.getBackground();
		installOwnMultiPromptBehaviour();
		associatedBdvLabelComponent.setText(" Associated to: "+bdvWindowTitle);
		touchUpForBdv();
	}
	private final BdvPrompts<?,OT> annotator;
	private SamjResponder<OT> currentSamjResponder = null;

	@Override
	protected void makeVisibleOnInstantiation() {
		//intentionally empty to introduce the behaviour that this GUI
		//is *not* displayed immediately after the GUI (class) is constructed
		//(which is BTW the default behaviour of MainGui)
	}

	private void installOwnMultiPromptBehaviour() {
		Context ctx = LocalDetachedContext.getContext();
		ScriptService ss = ctx.getService(ScriptService.class);
		ModuleService ms = ctx.getService(ModuleService.class);
		if (ss == null || ms == null) {
			//failing to install the seeding functionality, disable thus the "prompts" card
			radioButton2.setEnabled(false);
			radioButton2_isAllowedToBeUsed = false;
			return;
		}
		seedsService = new MultiPromptsWithScript<>(ss,ms, new File(SCRIPT_DEFAULT_WRONG_PATH));
		scriptPathElem.setBackground(alertBgColor);
		annotator.installOwnMultiPromptBehaviour(seedsService);
	}
	boolean radioButton2_isAllowedToBeUsed = true;
	MultiPromptsWithScript<OT> seedsService = null;

	private void passScriptPathToSeedService() {
		Path scriptPath = Paths.get(scriptPathElem.getText());
		File scriptFile = scriptPath != null ? scriptPath.toFile() : null;
		seedsService.setScriptPath(scriptFile);
		scriptPathElem.setBackground(scriptFile != null && scriptFile.exists() ? defaultBgColor : alertBgColor);
	}


	@Override
	protected JPanel createFirstComponent() {
		JPanel origPanel = super.createFirstComponent();
		origPanel.remove(cmbImages);
		origPanel.remove(go);
		cmbModels.setPreferredSize(new Dimension(0, (int)(0.07*MAIN_VERTICAL_SIZE)));

		//copied from MainGUI
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2); // Adjust insets as needed
		gbc.gridy = 1;
		gbc.gridx = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 10.0;
		gbc.fill = GridBagConstraints.BOTH;
		associatedBdvLabelComponent = new JLabel();
		origPanel.add(associatedBdvLabelComponent, gbc);

		origPanel.setPreferredSize(new Dimension(0, (int)(0.1*MAIN_VERTICAL_SIZE)));
		return origPanel;
	}
	protected JLabel associatedBdvLabelComponent;


	@Override
	protected JPanel createSecondComponent() {
		JPanel origPanel = super.createSecondComponent();
		cardPanel.remove(0);
		cardPanel.remove(0);

		JPanel card1 = new JPanel(new MigLayout("fill","center","[]push"));
		htmlText = new JTextPane();
		htmlText.setContentType("text/html");
		htmlText.setText(SAMJ_CONTROLS_HELP_HTML);
		card1.add( new JScrollPane(htmlText,
				  JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED) );

		JPanel card2 = new JPanel(new MigLayout("fill","[c][c][c]"));
		scriptPathElem = new JTextField(SCRIPT_DEFAULT_WRONG_PATH);
		scriptPathElem.addActionListener((ignore) -> passScriptPathToSeedService());
		card2.add(scriptPathElem, new CC().grow().span());
		//
		JButton templateButton = new JButton("Template");
		templateButton.addActionListener((ignored) -> {
			MultiPromptsWithScript.showTemplateScriptInIJ1Editor(LocalDetachedContext.getContext());
		});
		card2.add(templateButton, new CC().grow(1));
		//
		JButton browseButton = new JButton("Browse");
		browseButton.addActionListener((ignored) -> {
			//try to extract basedir from the current script path
			Path scriptPath = Paths.get(scriptPathElem.getText());
			Path scriptPathParent = scriptPath != null ? scriptPath.getParent() : null;
			File scriptDir = scriptPathParent != null ? scriptPathParent.toFile() : null;
			JFileChooser chooser = scriptDir != null ? new JFileChooser(scriptDir) : new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			int res = chooser.showOpenDialog(this);
			if (res == JFileChooser.APPROVE_OPTION) {
				scriptPathElem.setText( chooser.getSelectedFile().getAbsolutePath() );
				passScriptPathToSeedService();
			}
		});
		card2.add(browseButton, new CC().grow(1));
		//
		scriptHowToRunInfo = new JLabel("<html><font size=\"3\">Activate seeds script<br/>with <b>J</b> key and <b>Prompt</b></font></html>");
		card2.add(scriptHowToRunInfo, new CC().grow(1).wrap());
		//
		promptsDebugCombo = new JComboBox<>(PROMPTS_DEBUGGING_OPTIONS);
		promptsDebugCombo.addItemListener((i) -> {
			if (i.getStateChange() == ItemEvent.SELECTED) {
				final int debugModeId = PROMPTS_DEBUGGING_OPTIONS.indexOf(i.getItem());
				switch (debugModeId) {
					case 1: annotator.setMultiPromptsSrcOnlyDebug(); break;
					case 2: annotator.setMultiPromptsMildDebug(); break;
					case 3: annotator.setMultiPromptsFullDebug(); break;
					default: annotator.setMultiPromptsNoDebug();
				}
			}
		});
		card2.add(promptsDebugCombo, new CC().grow().span());

		radioButton2.setText("Seeded prompts");
		cardPanel.add(card1, MANUAL_STR);
		cardPanel.add(card2, PRESET_STR);
		cardPanel.setPreferredSize(new Dimension(0, (int)(0.5*MAIN_VERTICAL_SIZE)));

		origPanel.setPreferredSize(new Dimension(0, (int)(0.7*MAIN_VERTICAL_SIZE)));
		return origPanel;
	}


	@Override
	protected JPanel createThirdComponent() {
		JPanel origPanel = super.createThirdComponent();
		origPanel.remove(chkRoiManager);
		origPanel.remove(export);

		//TODO: add "Show images submitted for encoding:"
		origPanel.setPreferredSize(new Dimension(0, (int)(0.2*MAIN_VERTICAL_SIZE)));
		return origPanel;
	}


	@Override
	protected void setTwoThirdsEnabled(boolean enabled) {
		//leave empty not to mess with (enable/disable elements in) this GUI window
	}

	protected void setLocalControlsEnabled(boolean newState) {
		//this is basically instead of setTwoThirdsEnabled(),
		//but adapted for the current shape of the GUI
		radioButton1.setEnabled(newState);
		radioButton2.setEnabled(newState & radioButton2_isAllowedToBeUsed);
		cardPanel.setEnabled(newState);
		if (htmlText != null) htmlText.setEnabled(newState);
		if (scriptHowToRunInfo != null) scriptHowToRunInfo.setEnabled(newState);
		retunLargest.setEnabled(newState);
		export.setEnabled(newState);
	}
	JTextPane htmlText;
	private final String SCRIPT_DEFAULT_WRONG_PATH = "Please, point on a Fiji script that calculates seeds.";
	JTextField scriptPathElem;
	final Color defaultBgColor; //  = scriptPathElem.getBackground();
	final Color alertBgColor = Color.RED;
	JLabel scriptHowToRunInfo;
	JComboBox<String> promptsDebugCombo;


	protected static int MAIN_HORIZONTAL_SIZE = 400;

	protected void touchUpForBdv() {
		//Enable/disable GUI controls based on the availability status of the selected network
		setLocalControlsEnabled( cmbModels.isModelInstalled(getCurrentlySelectedModelName()) );

		setSize(MAIN_HORIZONTAL_SIZE, MAIN_VERTICAL_SIZE);

		//The original GUI disables some of its controls (including the "Go" button)
		//when the models choosing panel is touched. When "returning" from this model
		//chooser and if an available model was selected, the "Go" button is enabled.
		//And only after the "Go" button is pressed (and an image is encoded), the
		//controls are enabled again (see MainGUI.loadModel()).
		//
		//However, here we don't have the "Go" button (as the BDV encodes automatically
		//on-demand), so we have to monitor when the "Go" button would have turned
		//enabled again (that is when models choosing is finished), and then run
		//model's switching routine and re-enabling of controls.
		//
		//hook our own listener
		JComboBox<String> cmbModelsComboBox = (JComboBox<String>)cmbModels.getComponents()[0];
		//NB: the casting is warranted by the fact that the ModelSelection class (cmbModels) extends ComboBox<String>
		cmbModelsComboBox.addItemListener((i) -> {
			if (i.getStateChange() == ItemEvent.SELECTED) {
				final String selectedModelName = (String)i.getItem();
				if (cmbModels.isModelInstalled(selectedModelName)) {
					enableSamJControlsForModel(selectedModelName);
				} else {
					disableSamJControls();
				}
			}
		});

		drawerPanel.addModelDrawerPanelListener(newGuiState -> {
				final String selectedModelName = getCurrentlySelectedModelName();
				if (newGuiState && cmbModels.isModelInstalled(selectedModelName)) {
					enableSamJControlsForModel(selectedModelName);
				} else {
					disableSamJControls();
				}
		});

		retunLargest.addItemListener((ignored) -> {
			if (currentSamjResponder != null) currentSamjResponder.returnLargestRoi = retunLargest.isSelected();
		});

		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		close.removeActionListener( close.getActionListeners()[0] ); //removes the original action
		close.addActionListener( (ignore) -> hideWindow() );

		//remove first all action listeners attached to the Help button
		while (help.getActionListeners().length > 0) {
			ActionListener l = help.getActionListeners()[0];
			help.removeActionListener(l);
		}
		help.addActionListener((ignored) -> Tools.openUrlInWebBrowser(BdvPrompts.URL_WITH_DEFAULT_BDV_HELP));
	}

	protected String getCurrentlySelectedModelName() {
		final JComboBox<String> cmbModelsComboBox = (JComboBox<String>)cmbModels.getComponents()[0];
		return (String)cmbModelsComboBox.getModel().getSelectedItem();
	}

	protected void enableSamJControlsForModel(final String modelName) {
		setLocalControlsEnabled(true);
		currentSamjResponder = BdvPromptsUtils.switchToThisNetwork(cmbModels.getModelByName(modelName), annotator);
		if (currentSamjResponder != null) currentSamjResponder.returnLargestRoi = retunLargest.isSelected();
		annotator.startPrompts();
		System.out.println("BDV switched to SAMJ model: "+currentSamjResponder.networkName);
	}

	protected void disableSamJControls() {
		setLocalControlsEnabled(false);
		annotator.stopPrompts();
		System.out.println("BDV disabled access to SAMJ functions");
	}

	public void hideWindow() {
		this.setVisible(false);
	}

	public void showWindow() {
		this.setVisible(true);
	}

	public static final String SAMJ_CONTROLS_HELP_HTML =
			"<html>The <b>Help</b> button below will open <i>SAMJ in Labkit tutorials</i> in a web browser" +
			" where more of SAMJ functionalities and key shortcuts are explained.<br/><br/>" +
			"Basic usage and Notes follows:<br/>" +
			"<b>Select a label</b> in the left panel, place mouse cursor over the image, <b>Hold down L</b> key and" +
			" <b>Move mouse</b> to start defining a rectangular region inside which SAMJ will operate. This is finished" +
			" when <b>L is released</b> and only then SAMJ will annotate the pixels with the selected label.<br/><br/>" +
			"Notes:<br/><b>-</b> Sometimes one has to click into the image panel so that it gets focus and can start" +
			" reading key strokes.<br/><b>-</b> <u>SAMJ is enabled</u> only when its <u>icon is activated</u>.<br/>" +
			"<b>-</b> Don't forget to install SAM network (triangle button above).</html>";

	public static final Vector<String> PROMPTS_DEBUGGING_OPTIONS
			= new Vector<>( Arrays.asList(
				"When executing, don't show anything extra.",
				"  ..., show only (one) cropped-out image.",
				"  ..., show four debug images.",
				"  ..., show all available debug images."
			) );

	final static ConsumerInterface emptyFakeConsumer = new ConsumerInterface() {
		@Override
		public List<ComboBoxItem> getListOfOpenImages() { return Collections.emptyList(); }
		@Override
		public List<Polygon> getPolygonsFromRoiManager() { return Collections.emptyList(); }
		@Override
		public void enableAddingToRoiManager(boolean shouldBeAdding) { /* intentionally empty */ }

		@Override
		public void exportImageLabeling() {
			System.out.println("export happens here");
		}

		@Override
		public Object getFocusedImage() { return null; }
		@Override
		public String getFocusedImageName() { return ""; }

		@Override
		public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> getFocusedImageAsRai() { return null; }
		@Override
		public List<int[]> getPointRoisOnFocusImage() { return Collections.emptyList(); }
		@Override
		public List<Rectangle> getRectRoisOnFocusImage() { return Collections.emptyList(); }
		@Override
		public void addPolygonsFromGUI(List<Mask> masks) { /* intentionally empty */ }
		@Override
		public void activateListeners() { /* intentionally empty */ }
		@Override
		public void deactivateListeners() { /* intentionally empty */ }
		@Override
		public void setFocusedImage(Object image) { /* intentionally empty */ }
		@Override
		public void deselectImage() { /* intentionally empty */ }
		@Override
		public void deletePointRoi(int[] pp) { /* intentionally empty */ }
		@Override
		public void deleteRectRoi(Rectangle rect) { /* intentionally empty */ }
		@Override
		public boolean isValidPromptSelected() { return false; }
		@Override
		public void notifyBatchSamize(String modelName, String maskPrompt) { /* intentionally empty */ }
	};
}
