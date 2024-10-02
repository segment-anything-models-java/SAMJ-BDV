package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
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

	@Parameter(label = "Show images submitted for encoding:")
	boolean showImagesSubmittedToNetwork = false;

	@Override
	public void run() {
		try {
			//start BDV on the file
			final BigDataViewer bdv = BigDataViewer.open(
					  inputXml.getAbsolutePath(),
					  inputXml.getAbsolutePath(),
					  new ProgressWriterConsole(),
					  new ViewerOptions());

			//add the SAMJ annotator to it
			final BdvPrompts<?, FloatType> annotator = new BdvPrompts<>(
					  bdv.getViewer(),
					  (SourceAndConverter)bdv.getViewer().state().getSources().get(0),
					  bdv.getViewerFrame().getTriggerbindings(),
					  "SAMJ prompts", new FloatType(), true);

			if (showImagesSubmittedToNetwork) annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );

			if (selectedNetwork.startsWith("Efficient")) {
				System.out.println("...working with Efficient SAM");
				annotator.addPromptsProcessor( new SamjResponder<>( new EfficientSAM() ));
			} else if (selectedNetwork.startsWith("SAM2 Tiny")) {
				System.out.println("...working with SAM2 Tiny");
				annotator.addPromptsProcessor( new SamjResponder<>( new SAM2Tiny() ));
			} else {
				//in any other case, just add the fake responder...
				System.out.println("...working with fake responses");
				annotator.addPromptsProcessor( new FakeResponder<>() );
			}
		} catch (SpimDataException e) {
			throw new RuntimeException(e);
		} catch (IOException|InterruptedException e) {
			System.out.println("Exception occurred during EfficientSAM initialization: "+e.getMessage());
		}
	}
}
