package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.ReportImageOnConsoleResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.EfficientSAM;
import ai.nets.samj.communication.model.SAM2Tiny;
import bdv.interactive.prompts.BdvPrompts;
import net.imagej.Dataset;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.io.IOException;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV on opened image")
public class PluginBdvOnOpenedImage implements Command {
	@Parameter
	Dataset inputImage;

	@Parameter(label = "Select network to use:",
			  choices = {"Efficient SAM", "SAM2 Tiny", "fake responses"})
			  //TODO use initializator to readout which networks are installed
	String selectedNetwork = "fake";

	@Parameter(label = "Image display mode:",
			  choices = {"Only original input image", "Original input image & original input image", "Inverted input image & original input image"})
	String displayMode = "Only";

	@Parameter(label = "Use only the largest ROIs:")
	boolean useLargestRois = true;

	@Parameter(label = "Show images submitted for encoding:")
	boolean showImagesSubmittedToNetwork = false;

	@Parameter(label = "Show images for multi-prompter ('J'-mode):",
			  choices = {"Don't show anything extra", "Four debug images", "All possible debug images"})
	String multiPrompterVisualDebug = "Don't";

	@Override
	public void run() {
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		annotateWithBDV( (Img)origImage );
	}

	public <T extends RealType<T>> BdvPrompts<T,FloatType> annotateWithBDV(final Img<T> img) {
		//determine the image's min and max pixel value
		double[] imgMinMaxVals = new double[] { img.firstElement().getRealDouble(), img.firstElement().getRealDouble() };
		img.forEach(px -> {
			double val = px.getRealDouble();
			imgMinMaxVals[0] = Math.min(imgMinMaxVals[0], val);
			imgMinMaxVals[1] = Math.max(imgMinMaxVals[1], val);
		});
		final double imgIntRange = Math.max(1.0, imgMinMaxVals[1]-imgMinMaxVals[0]);

		//prepare normalized and inverted-normalized views of the original image
		final RandomAccessibleInterval<T> originalNormalizedImg
				  = Converters.convert((RandomAccessibleInterval<T>)img, (s, t) -> t.setReal((s.getRealDouble()-imgMinMaxVals[0])/imgIntRange), img.firstElement());
		final RandomAccessibleInterval<T> invertedNormalizedImg
				  = Converters.convert((RandomAccessibleInterval<T>)img, (s, t) -> t.setReal((imgMinMaxVals[1]-s.getRealDouble())/imgIntRange), img.firstElement());

		final BdvPrompts<T, FloatType> annotator;
		if (displayMode.startsWith("Original")) {
			annotator = new BdvPrompts<>(originalNormalizedImg, "Input image", originalNormalizedImg, "Original image", "SAMJ", new FloatType());
		} else if (displayMode.startsWith("Inverted")) {
			annotator = new BdvPrompts<>(invertedNormalizedImg, "Input inverted image", originalNormalizedImg, "Original image", "SAMJ", new FloatType());
		} else {
			annotator = new BdvPrompts<>(originalNormalizedImg, "Input image", "SAMJ", new FloatType());
		}

		annotator.installDefaultMultiSelectBehaviour();
		annotator.enableShowingPolygons();
		if (showImagesSubmittedToNetwork) {
			annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );
		}

		if (multiPrompterVisualDebug.startsWith("Four")) {
			annotator.setMultiPromptsMildDebug();
		} else if (multiPrompterVisualDebug.startsWith("All")) {
			annotator.setMultiPromptsFullDebug();
		} else {
			annotator.setMultiPromptsNoDebug();
		}

		try {
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
		} catch (IOException|InterruptedException e) {
			System.out.println("Exception occurred during EfficientSAM initialization: "+e.getMessage());
			return null;
		}

		return annotator;
	}


	public static void main(String[] args) {
		final BdvPrompts<?,?> annotator = new PluginBdvOnOpenedImage()
			.annotateWithBDV( SimplifiedIO.openImage(
				"/home/ulman/devel/HackBrno23/HackBrno23_introIntoImglib2AndBDV__SOLUTION/src/main/resources/t1-head.tif"
			).getImg() );
		annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );
		annotator.addPromptsProcessor( new ReportImageOnConsoleResponder<>() );
	}
}
