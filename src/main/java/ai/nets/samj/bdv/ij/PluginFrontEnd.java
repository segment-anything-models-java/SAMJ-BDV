package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.SAMJ_BDV_Annotator;
import net.imagej.Dataset;
import net.imglib2.img.Img;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV demo")
public class PluginFrontEnd implements Command {
	@Parameter
	Dataset inputImage;

	@Override
	public void run() {
		new SAMJ_BDV_Annotator().startBdvAnnotation((Img)inputImage.getImgPlus().getImg());
	}
}
