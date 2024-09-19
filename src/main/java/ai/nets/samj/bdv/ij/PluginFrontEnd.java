package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.EfficientSAM;
import bdv.interactive.prompts.BdvPrompts;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.io.IOException;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV demo")
public class PluginFrontEnd implements Command {
	@Parameter
	Dataset inputImage;

	@Parameter
	boolean showImagesSubmittedToSAM = false;

	@Parameter
	boolean useFakePromptResults = false;

	@Override
	public void run() {
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		//TODO: make sure it is a 3D image

		run((Img)origImage);
	}


	public static void main(String[] args) {
		ImgPlus image = SimplifiedIO.openImage("/home/ulman/devel/HackBrno23/HackBrno23_introIntoImglib2AndBDV__SOLUTION/src/main/resources/t1-head.tif");
		//make border pixels a bit brighter
		//img.forEach(p -> { if (p.getRealDouble() == 0.0) p.setReal(15); });
		run(image.getImg());
	}

	public static <T extends RealType<T>> void run(final Img<T> img) {
		BdvPrompts<T> annotator = new BdvPrompts<>(img).enableShowingPolygons();
		annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );
		//annotator.addPromptsProcessor( new FakeResponder<>() );

		try {
			annotator.addPromptsProcessor( new SamjResponder<>( new EfficientSAM() ));
		} catch (IOException|InterruptedException e) {
			System.out.println("Exception occurred during EfficientSAM initialization: "+e.getMessage());
		}
	}
}
