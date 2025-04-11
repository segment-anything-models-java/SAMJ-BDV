package ai.nets.samj.gui;

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

import static bdv.interactive.prompts.BdvPromptsUtils.findAnySamNetworkOrNull;
import static bdv.interactive.prompts.BdvPromptsUtils.findShowImageInIjResponderOrNull;
import static bdv.interactive.prompts.BdvPromptsUtils.switchToThisNetwork;

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

		//pre-created before its block only because this variable is used in the next block
		doOnlyLargestRoi = new JCheckBox();

		add( new JLabel("Select network to use:") );
		networksModel = new Vector<>(10);
		networksChooser = new JComboBox<>(networksModel);
		networksChooser.addItemListener((i) -> {
			if (i.getStateChange() == ItemEvent.SELECTED) {
				SAMModel model = findNetWorkModel((String) i.getItem());
				SamjResponder<OT> samj = switchToThisNetwork(model, annotator);
				if (samj != null) samj.returnLargestRoi = doOnlyLargestRoi.isSelected();
			}
		});
		add(networksChooser);

		add( new JLabel("<html><i>Please, use Fiji -> Plugins -> SAMJ -> SAMJ Annotator to manage installed<br/>" +
				  "networks. Afterwards, please open this dialog to have the list above updated.</i></html>"),
				  new CC().alignX("center").span().wrap("15") );

		add( new JLabel("Use only the largest ROIs:") );
		//doOnlyLargestRoi = new JCheckBox(); -- created already above
		doOnlyLargestRoi.addItemListener((ignore) -> {
			BdvPrompts.PromptsProcessor<OT> p = findAnySamNetworkOrNull(annotator);
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
				if (findShowImageInIjResponderOrNull(annotator) == null) {
					annotator.addPromptsProcessor( new ShowImageInIJResponder<OT>() );
				}
			} else {
				BdvPrompts.PromptsProcessor<OT> p = findShowImageInIjResponderOrNull(annotator);
				if (p != null) {
					annotator.removePromptsProcessor(p);
				}
			}
		});
		add(doDisplayEncodedImage);

		updateContent();
	}

	public void updateContent() {
		networksModel.clear();
		enlistAvailableNetworks(networksModel);
		networksChooser.setModel(new DefaultComboBoxModel<>(networksModel));

		BdvPrompts.PromptsProcessor<OT> p = findAnySamNetworkOrNull(annotator);
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

		doDisplayEncodedImage.setSelected(findShowImageInIjResponderOrNull(annotator) != null);
	}

	final Vector<String> networksModel;
	final JComboBox<String> networksChooser;
	final JCheckBox doOnlyLargestRoi;
	final JCheckBox doDisplayEncodedImage;
	final BdvPrompts<IT,OT> annotator;
	AvailableNetworksFactory availableNetworks = null;

	private void enlistAvailableNetworks(final Vector<String> list) {
		availableNetworks = new AvailableNetworksFactory();
		list.addAll( availableNetworks.availableModels() );
		list.add( "fake responses" );
	}

	private SAMModel findNetWorkModel(final String networkName) {
		if (availableNetworks == null) availableNetworks = new AvailableNetworksFactory();
		return availableNetworks.getModel(networkName);
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
