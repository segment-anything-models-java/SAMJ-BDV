package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.util.AvailableNetworksFactory;
import ai.nets.samj.util.MultiPromptsWithScript;
import bdv.interactive.prompts.BdvPrompts;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.widget.FileWidget;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV on opened image (with seeds script)")
public class PluginBdvMultiPromptingOnOpenedImage extends DynamicCommand {
	@Parameter
	Dataset inputImage;

	@Parameter(label = "Select network to use:", initializer = "listAvailableNetworks")
	String selectedNetwork = "fake";

	void listAvailableNetworks() {
		final List<String> choicesList = new ArrayList<>(10);
		choicesList.addAll( availableNetworks.availableModels() );
		choicesList.add( "fake responses" );
		this.getInfo()
			  .getMutableInput("selectedNetwork", String.class)
			  .setChoices( choicesList );
	}
	//
	private final AvailableNetworksFactory availableNetworks = new AvailableNetworksFactory();

	@Parameter(label = "Use only the largest ROIs:")
	boolean useLargestRois = true;

	@Parameter(label = "Jython script that detects seeds:", style = FileWidget.OPEN_STYLE)
	File scriptFile;

	@Parameter(label = "CLICK ME NOW to open template script:",
			  persist = false, callback = "buttonClicked")
	boolean toggleAsButton = false;

	void buttonClicked() {
		MultiPromptsWithScript.showTemplateScriptInIJ1Editor(scriptService.context());
	}

	@Parameter
	ScriptService scriptService;
	@Parameter
	ModuleService moduleService;

	@Parameter(label = "Show images submitted for encoding:")
	boolean showImagesSubmittedToNetwork = false;

	@Parameter(label = "Show images for multi-prompter ('J'-mode):",
			  choices = {"Don't show anything extra", "Only cropped-out image", "Four debug images", "All possible debug images"})
	String multiPrompterVisualDebug = "Don't";

	@Override
	public void run() {
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		annotateWithBDV( (Img)origImage );
	}

	public <T extends RealType<T>> BdvPrompts<T,FloatType> annotateWithBDV(final Img<T> img) {
		final BdvPrompts<T, FloatType> annotator
				  = new BdvPrompts<>(img, "Input image", "SAMJ", new FloatType());

		annotator.installOwnMultiPromptBehaviour(
				  new MultiPromptsWithScript<>(scriptService,moduleService,scriptFile),
				  "bdvprompts_rectangle_user_seeds","J"
		);

		annotator.enableShowingPolygons();
		if (showImagesSubmittedToNetwork) {
			annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );
		}

		if (multiPrompterVisualDebug.startsWith("Only")) {
			annotator.setMultiPromptsSrcOnlyDebug();
		} else if (multiPrompterVisualDebug.startsWith("Four")) {
			annotator.setMultiPromptsMildDebug();
		} else if (multiPrompterVisualDebug.startsWith("All")) {
			annotator.setMultiPromptsFullDebug();
		} else {
			annotator.setMultiPromptsNoDebug();
		}

		System.out.println("...working with "+selectedNetwork);
		SAMModel model = availableNetworks.getModel(selectedNetwork);
		if (model != null) {
			SamjResponder<FloatType> samj = new SamjResponder<>(model);
			samj.returnLargestRoi = useLargestRois;
			annotator.addPromptsProcessor(samj);
		} else {
			annotator.addPromptsProcessor( new FakeResponder<>() );
		}

		return annotator;
	}

	public static void main(String[] args) {
		try {
			ImageJ ij = new ImageJ();
			ij.ui().showUI();
			Object I = ij.io().open("/home/ulman/data/DroneWell/auto-sikmo-silnice/DJI_20240324211017_8125_V-1.tif");
			ij.ui().show(I);
			ij.command().run(PluginBdvMultiPromptingOnOpenedImage.class, true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
