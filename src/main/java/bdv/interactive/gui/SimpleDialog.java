package bdv.interactive.gui;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.util.AvailableNetworksFactory;
import bdv.interactive.prompts.BdvPrompts;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.miginfocom.layout.LC;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.CC;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.Vector;

// The generics IT,OT are needed only for the commanded 'annotator'.
public class SimpleDialog<IT extends RealType<IT>, OT extends RealType<OT> & NativeType<OT>> extends JPanel {
	public SimpleDialog(final BdvPrompts<IT,OT> annotator, final String windowTitle) {
		super(new MigLayout(new LC().wrapAfter(2).fill(), new AC().align("right").gap("20").align("left")));
		this.frameTitle = windowTitle;

		this.annotator = annotator;

		//create content
		JLabel title = new JLabel();
		title.setIcon( new ImageIcon(this.getClass().getResource("SAMJ_logo.png")) );
		add(title, new CC().alignX("center").span().wrap("15"));

		add( new JLabel("Select network to use:") );
		networksModel = new Vector<>(10);
		networksChooser = new JComboBox<>(networksModel);
		networksChooser.addItemListener((i) -> {
			if (i.getStateChange() == ItemEvent.SELECTED)
				switchToThisNetwork((String)i.getItem());
		});
		add(networksChooser);

		add( new JLabel("<html><i>Please, use Fiji -> Plugins -> SAMJ -> SAMJ Annotator to manage installed<br/>" +
				  "networks. Afterwards, please open this dialog to have the list above updated.</i></html>"),
				  new CC().alignX("center").span().wrap("15") );

		add( new JLabel("Use only the largest ROIs:") );
		doOnlyLargestRoi = new JCheckBox();
		doOnlyLargestRoi.addItemListener((ignore) -> {
			BdvPrompts.PromptsProcessor<OT> p = findAnySamNetworkOrNull();
			if (p != null) {
				SamjResponder<OT> sam = (SamjResponder<OT>) p;
				sam.returnLargestRoi = doOnlyLargestRoi.isSelected();
			}
		});
		add(doOnlyLargestRoi);

		add( new JLabel("Show images submitted for encoding:") );
		doDisplayEncodedImage = new JCheckBox();
		doDisplayEncodedImage.addItemListener((ignore) -> {
			if (doDisplayEncodedImage.isSelected()) {
				if (findShowImageInIjResponderOrNull() == null) {
					System.out.println("Registering the displaying of an image in IJ.");
					annotator.addPromptsProcessor( new ShowImageInIJResponder<OT>() );
				} else {
					System.out.println("Displaying of image in IJ already enabled.");
				}
			} else {
				BdvPrompts.PromptsProcessor<OT> p = findShowImageInIjResponderOrNull();
				if (p != null) {
					annotator.removePromptsProcessor(p);
					System.out.println("Unregistering the display of an image in IJ.");
				} else {
					System.out.println("Displaying of image in IJ was already turned off.");
				}
			}
		});
		add(doDisplayEncodedImage);

		updateContent();
	}

	public void updateContent() {
		System.out.println("updateing from the given samjFill");

		networksModel.clear();
		enlistAvailableNetworks(networksModel);
		networksChooser.removeAllItems();
		for (String net : networksModel) networksChooser.addItem(net);

		BdvPrompts.PromptsProcessor<OT> p = findAnySamNetworkOrNull();
		if (p != null) {
			SamjResponder<OT> sam = (SamjResponder<OT>) p;
			for (int i = 0; i < networksModel.size(); ++i) {
				if (networksModel.get(i).equals(sam.networkName)) networksChooser.setSelectedIndex(i);
			}
			doOnlyLargestRoi.setSelected( sam.returnLargestRoi );
		} else {
			//fake responder I guess
			networksChooser.setSelectedIndex( networksModel.size()-1 ); //NB: fake network is listed always the last
			doOnlyLargestRoi.setSelected( false );
		}

		doDisplayEncodedImage.setSelected(findShowImageInIjResponderOrNull() != null);
	}

	final Vector<String> networksModel;
	final JComboBox<String> networksChooser;
	final JCheckBox doOnlyLargestRoi;
	final JCheckBox doDisplayEncodedImage;
	final BdvPrompts<IT,OT> annotator;
	final AvailableNetworksFactory availableNetworks = new AvailableNetworksFactory();

	private void enlistAvailableNetworks(final Vector<String> list) {
		list.addAll( availableNetworks.availableModels() );
		list.add( "fake responses" );
	}

	private void switchToThisNetwork(final String networkName) {
		//first, remove any network from the list of prompters
		BdvPrompts.PromptsProcessor<OT> p = findAnySamNetworkOrNull();
		while (p != null) {
			annotator.removePromptsProcessor(p);
			p = findAnySamNetworkOrNull();
		}

		//now, add the given one
		SAMModel model = availableNetworks.getModel(networkName);
		if (model != null) {
			SamjResponder<OT> samj = new SamjResponder<>(model);
			samj.returnLargestRoi = doOnlyLargestRoi.isSelected();
			annotator.addPromptsProcessor(samj);
		} else {
			annotator.addPromptsProcessor( new FakeResponder<>() );
		}
	}

	private BdvPrompts.PromptsProcessor<OT> findAnySamNetworkOrNull() {
		for (BdvPrompts.PromptsProcessor<OT> p : annotator.listPromptsProcessors()) {
			if (p instanceof SamjResponder) return p;
		}
		return null;
	}

	private BdvPrompts.PromptsProcessor<OT> findShowImageInIjResponderOrNull() {
		for (BdvPrompts.PromptsProcessor<OT> p : annotator.listPromptsProcessors()) {
			if (p instanceof ShowImageInIJResponder) return p;
		}
		return null;
	}

	// ---------------------------------------------------------------
	public void showWindow() {
		if (frame == null) {
			frame = new JFrame(frameTitle);
			frame.add(this);
			updateContent();
			frame.pack();
		} else {
			updateContent();
		}
		frame.setVisible(true);
	}

	public void closeWindow() {
		if (frame != null) frame.setVisible(false);
	}

	private JFrame frame = null;
	private final String frameTitle;
}
