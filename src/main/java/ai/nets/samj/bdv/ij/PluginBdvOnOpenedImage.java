package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.polygonconsumers.PolygonsRasterizerInto3dImgConsumer;
import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.ReportImageOnConsoleResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.EfficientSAM;
import ai.nets.samj.communication.model.SAM2Tiny;
import bdv.interactive.prompts.BdvPrompts;
import bdv.util.BdvFunctions;
import net.imagej.Dataset;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
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

	@Parameter(label = "Select network to use: ",
			  choices = {"Efficient SAM", "SAM2 Tiny", "fake responses"})
			  //TODO use initializator to readout which networks are installed
	String selectedNetwork = "Efficient";

	@Parameter(label = "Show images submitted for encoding: ")
	boolean showImagesSubmittedToNetwork = false;

	@Override
	public void run() {
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		annotateWithBDV( (Img)origImage );
	}

	public <T extends RealType<T>> BdvPrompts<T,FloatType> annotateWithBDV(final Img<T> img) {
		final BdvPrompts<T, FloatType> annotator
				  = new BdvPrompts<>(img, new FloatType()).enableShowingPolygons();

		if (showImagesSubmittedToNetwork) annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );

		try {
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
		} catch (IOException|InterruptedException e) {
			System.out.println("Exception occurred during EfficientSAM initialization: "+e.getMessage());
			return null;
		}

		return annotator;
	}


	public static void main(String[] args) {
		Img<UnsignedByteType> rawImg = SimplifiedIO.openImage(
				"/home/ulman/devel/HackBrno23/HackBrno23_introIntoImglib2AndBDV__SOLUTION/src/main/resources/t1-head.tif",
				new UnsignedByteType() ).getImg();
		rawImg.forEach(p -> p.setReal(p.getRealFloat()+5)); //make the background visible in BDV

		final BdvPrompts<?,?> annotator = new PluginBdvOnOpenedImage().annotateWithBDV( rawImg );
		//annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );
		annotator.addPromptsProcessor( new ReportImageOnConsoleResponder<>() );

		Img<UnsignedByteType> maskImg = rawImg.copy();
		maskImg.forEach(GenericByteType::setZero);
		BdvFunctions.show(maskImg, "polygons as masks");

		annotator.addPolygonsConsumer( new PolygonsRasterizerInto3dImgConsumer<>(maskImg) );
		annotator.setExportImage(maskImg); //...to tell 'P' behaviour what image to save on a hard drive
	}
}
