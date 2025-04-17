package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.ReportImageOnConsoleResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.gui.BDVedMainGUI;
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
import java.util.Collections;
import java.util.List;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>BigDataViewer>BDV with SAMJ on opened image")
public class PluginBdvOnOpenedImage extends DynamicCommand {
	@Parameter
	Dataset inputImage;

	/*
	@Parameter(label = "Image display mode:",
			  choices = {"Normally only original input image", "Original input image & Original input image", "Inverted input image & Original input image"})
	*/
	String displayMode = "Normally";

	@Parameter(label = "Show images submitted for encoding:")
	boolean showImagesSubmittedToNetwork = false;

	@Override
	public void run() {
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		annotateWithBDV( (Img)origImage );
	}

	public <T extends RealType<T>> BdvPrompts<T,FloatType> annotateWithBDV(final Img<T> img) {
		final BdvPrompts<T, FloatType> annotator;
		if (displayMode.startsWith("Normally")) {
			annotator = new BdvPrompts<>(img, inputImage.getName(), "SAMJ", new FloatType());
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

		//install SAMJ into the BDV's CardPanel
		final BDVedMainGUI<?> samjDialog = new BDVedMainGUI<>(annotator, inputImage.getName());
		BDVedMainGUI.installToCardsPanel(annotator.getCardPanelIfKnown(), samjDialog);

		final SAMModel net = AvailableNetworksFactory.reportAndChooseFirstAvailable(annotator, s -> System.out.println("BigDataViewer: " + s));
		if (net != null) {
			System.out.println("BigDataViewer: Using initially the first-one listed. Change it by clicking the SAMJ button in the collapsed, right-hand-side panel in the BigDataViewer.");
		} else {
			System.err.println("BigDataViewer: No SAMJ model installed yet. Please, click the SAMJ button in the collapsed, right-hand-side panel in the BigDataViewer to configure SAMJ.");
		}

		annotator.enableShowingPolygons();
		if (showImagesSubmittedToNetwork) annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );

		if (img.numDimensions() > 2) {
			System.out.println("BigDataViewer: Detected 3D image, enabling 'perSlices' SAMJ annotations.");
			annotator.installRepeatPromptOnNextSliceBehaviour();
			//TODO: presence indicator requires very fast query if a coordinate is within a prompt (polygon)
			//annotator.installPerSlicesTrackingPromptBehaviour(new LabelPresenceIndicatorAtGlobalCoord());
			annotator.installSideViewsBehaviour();
		}

		return annotator;
	}
}
