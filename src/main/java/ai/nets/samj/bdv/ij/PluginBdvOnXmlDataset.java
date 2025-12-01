package ai.nets.samj.bdv.ij;
import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.ReportImageOnConsoleResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.gui.BDVedMainGUI;
import ai.nets.samj.util.AvailableNetworksFactory;
import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.interactive.prompts.BdvPrompts;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>BigDataViewer>BDV with SAMJ on XML file")
public class PluginBdvOnXmlDataset extends DynamicCommand {
	@Parameter(label = "BDV handle XML:", style = FileWidget.OPEN_STYLE)
	File inputXml;

	@Parameter(label = "Show images submitted for encoding:")
	boolean showImagesSubmittedToNetwork = false;

	@Override
	public void run() {
		annotateWithBDV(inputXml);
	}

	public BdvPrompts<?,FloatType> annotateWithBDV(final File pathToXmlFile) {
		final BdvPrompts<?, FloatType> annotator;
		try {
			//start BDV on the file
			final BigDataViewer bdv = BigDataViewer.open(
					  pathToXmlFile.getAbsolutePath(),
					  pathToXmlFile.getAbsolutePath(),
					  new ProgressWriterConsole(),
					  new ViewerOptions());

			//add the SAMJ annotator to it
			SourceAndConverter<?> sac = bdv.getViewer().state().getSources().get(0);
			annotator = new BdvPrompts<>(
					  bdv.getViewer(),
					  (SourceAndConverter)sac,
					  bdv.getConverterSetups().getConverterSetup(sac),
					  bdv.getViewerFrame().getTriggerbindings(),
					  "SAMJ prompts", new FloatType(), true);

			//install SAMJ into the BDV's CardPanel
			final BDVedMainGUI<?> samjDialog = new BDVedMainGUI<>(annotator, bdv.getViewerFrame().getTitle());
			BDVedMainGUI.installToCardsPanel(bdv.getViewerFrame().getCardPanel(), samjDialog);

			final SAMModel net = AvailableNetworksFactory.reportAndChooseFirstAvailable(annotator, s -> System.out.println("BigDataViewer: " + s));
			if (net != null) {
				System.out.println("BigDataViewer: Using initially the first-one listed. Change it by clicking the SAMJ button in the collapsed, right-hand-side panel in the BigDataViewer.");
			} else {
				System.err.println("BigDataViewer: No SAMJ model installed yet. Please, click the SAMJ button in the collapsed, right-hand-side panel in the BigDataViewer to configure SAMJ.");
			}

			annotator.enableShowingPolygons();
			if (showImagesSubmittedToNetwork) annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );

			if (sac.getSpimSource().getSource(0,0).numDimensions() > 2) {
				System.out.println("BigDataViewer: Detected 3D image, enabling 'perSlices' SAMJ annotations.");
				annotator.installRepeatPromptOnNextSliceBehaviour();
				//TODO: presence indicator requires very fast query if a coordinate is within a prompt (polygon)
				//annotator.installPerSlicesTrackingPromptBehaviour(new LabelPresenceIndicatorAtGlobalCoord());
				annotator.installSideViewsBehaviour();
			}

		} catch (SpimDataException e) {
			System.out.println("Exception occurred during loading of the XML dataset: "+e.getMessage());
			return null;
		}

		return annotator;
	}


	public static void main(String[] args) {
		final BdvPrompts<?,?> annotator = new PluginBdvOnXmlDataset()
				  .annotateWithBDV( new File("/home/ulman/data/Mette_E1_hdf5/dataset_hdf5.xml") );
		annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );
		annotator.addPromptsProcessor( new ReportImageOnConsoleResponder<>() );
	}
}
