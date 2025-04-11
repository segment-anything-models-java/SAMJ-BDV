package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.ReportImageOnConsoleResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.util.AvailableNetworksFactory;
import bdv.interactive.prompts.BdvPrompts;
import net.imagej.Dataset;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.simplifiedio.SimplifiedIO;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>BigDataViewer>BDV with SAMJ on opened image")
public class PluginBdvOnOpenedImage extends DynamicCommand {
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

	/*
	@Parameter(label = "Image display mode:",
			  choices = {"Normally only original input image", "Original input image & Original input image", "Inverted input image & Original input image"})
	*/
	String displayMode = "Normally";

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
		final BdvPrompts<T, FloatType> annotator;
		if (displayMode.startsWith("Normally")) {
			annotator = new BdvPrompts<>(img, "Input image", "SAMJ", new FloatType());
		} else if (displayMode.startsWith("Original")) {
			annotator = new BdvPrompts<>(img, "Input image", img, "Original image", "SAMJ", new FloatType());
		} else {
			//determine the image's min and max pixel value
			double[] imgMinMaxVals = new double[] { img.firstElement().getRealDouble(), img.firstElement().getRealDouble() };
			img.forEach(px -> {
				double val = px.getRealDouble();
				imgMinMaxVals[0] = Math.min(imgMinMaxVals[0], val);
				imgMinMaxVals[1] = Math.max(imgMinMaxVals[1], val);
			});

			//prepare normalized and inverted-normalized views of the original image
			final RandomAccessibleInterval<T> invertedImg
					  = Converters.convert((RandomAccessibleInterval<T>)img, (s, t) -> t.setReal(imgMinMaxVals[1]-s.getRealDouble()), img.firstElement());

			annotator = new BdvPrompts<>(invertedImg, "Input inverted image", img, "Original image", "SAMJ", new FloatType());
		}

		annotator.installDefaultMultiPromptBehaviour();
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
}
