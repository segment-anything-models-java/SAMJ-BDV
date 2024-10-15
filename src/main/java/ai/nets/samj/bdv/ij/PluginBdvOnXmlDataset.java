package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.ReportImageOnConsoleResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.EfficientSAM;
import ai.nets.samj.communication.model.SAM2Tiny;
import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.interactive.prompts.BdvPrompts;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.io.IOException;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV on XML file")
public class PluginBdvOnXmlDataset implements Command {
	@Parameter(style = FileWidget.OPEN_STYLE)
	File inputXml;

	@Parameter(label = "Select network to use: ",
			  choices = {"Efficient SAM", "SAM2 Tiny", "fake responses"})
			  //TODO use initializator to readout which networks are installed
	String selectedNetwork = "fake";

	@Parameter(label = "Use only the largest ROIs: ")
	boolean useLargestRois = true;

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
			annotator.enableShowingPolygons();

			if (showImagesSubmittedToNetwork) annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );

			if (selectedNetwork.startsWith("Efficient")) {
				System.out.println("...working with Efficient SAM");
				SamjResponder<FloatType> samj = new SamjResponder<>(new EfficientSAM());
				samj.returnLargestRoi = useLargestRois;
				annotator.addPromptsProcessor(samj);
			} else if (selectedNetwork.startsWith("SAM2 Tiny")) {
				System.out.println("...working with SAM2 Tiny");
				SamjResponder<FloatType> samj = new SamjResponder<>(new SAM2Tiny());
				samj.returnLargestRoi = useLargestRois;
				annotator.addPromptsProcessor(samj);
			} else {
				//in any other case, just add the fake responder...
				System.out.println("...working with fake responses");
				annotator.addPromptsProcessor( new FakeResponder<>() );
			}
		} catch (SpimDataException e) {
			System.out.println("Exception occurred during loading of the XML dataset: "+e.getMessage());
			return null;
		} catch (IOException|InterruptedException e) {
			System.out.println("Exception occurred during EfficientSAM initialization: "+e.getMessage());
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
