package ai.nets.samj.util;

import bdv.interactive.prompts.BdvPrompts;
import bdv.tools.brightness.ConverterSetup;
import net.imagej.legacy.LegacyService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.Context;
import org.scijava.module.Module;
import org.scijava.module.ModuleService;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptService;
import java.io.File;
import java.io.StringReader;

public class MultiPromptsWithScript <T extends RealType<T> & NativeType<T>> implements BdvPrompts.SeedsFromPromptCreator<T> {

	final ScriptService scriptService;
	final ModuleService moduleService;
	File scriptFile;
	int scriptWaitingTimeMins = 1;

	public MultiPromptsWithScript(final ScriptService scriptService, final ModuleService moduleService, final File scriptFile) {
		if (scriptService == null || scriptFile == null) {
			throw new IllegalArgumentException("Both service and file path must be non-null!");
		}

		this.scriptService = scriptService;
		this.moduleService = moduleService;
		this.scriptFile = scriptFile;
	}

	public void setScriptPath(final File newScriptFile) {
		this.scriptFile = newScriptFile;
	}

	public void setWaitingTime(final int newWaitingTimeMins) {
		this.scriptWaitingTimeMins = newWaitingTimeMins;
	}

	@Override
	public RandomAccessibleInterval<T> establishBinarySeeds(RandomAccessibleInterval<T> inputImageToEstablishSeedsHere,
	                                                        ConverterSetup considerThisIntensityScaling,
	                                                        int bitFieldForRequestedDebugImages) {
		final ImgPlusOverImgSharedMem extImg = ImgPlusOverImgSharedMem.cloneThis(inputImageToEstablishSeedsHere);

		if ((bitFieldForRequestedDebugImages & Prompts.SHOW_ORIGINAL_DBGIMAGE) > 0) {
			ImageJFunctions.show(inputImageToEstablishSeedsHere, Prompts.getDebugImagesCounter() + ": source original image");
		}
		if ((bitFieldForRequestedDebugImages & Prompts.SHOW_SOURCE_DBGIMAGE) > 0) {
			ImageJFunctions.show(extImg.floatTypeImg, Prompts.getDebugImagesCounter() + ": original image shared with the script");
		}

		try {
			final Module module = moduleService.createModule( scriptService.getScript(scriptFile) );
			module.setInput("imp", extImg.floatImagePlus);
			module.setInput("contrast_min", considerThisIntensityScaling.getDisplayRangeMin());
			module.setInput("contrast_max", considerThisIntensityScaling.getDisplayRangeMax());
			System.out.println("==> Executing external script: "+scriptFile);
			module.run();
			System.out.println("==> External script finished now.");

			if ((bitFieldForRequestedDebugImages & Prompts.SHOW_THRESHOLDED_DBGIMAGE) > 0) {
				ImageJFunctions.show(extImg.floatTypeImg, Prompts.getDebugImagesCounter() + ": seeds image obtained from the script");
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed executing seeds script: "+e.getMessage(), e);
		}

		//since the extImg is _always_ float (by its design), we have to convert back to provided type T
		final T inputTypeExplicitly = inputImageToEstablishSeedsHere.getAt(inputImageToEstablishSeedsHere.minAsLongArray());
		return Converters.convert(
				  (RandomAccessibleInterval<FloatType>)extImg.floatTypeImg,
				  (i,o) -> o.setReal(i.getRealDouble()),
				  inputTypeExplicitly
		);
	}

	public static void showTemplateScriptInIJ1Editor(final Context context) {
		ScriptInfo s = new ScriptInfo(context, "seeds extractor.py", new StringReader(templateScriptItself()));
		context.getService(LegacyService.class).openScriptInTextEditor(s);
	}

	public static String templateScriptItself() {
		return
			"# RESAVE THIS SEEDS SCRIPT AND POINT THE BDV_WITH_SEEDS DIALOG ON IT\n"+
			"\n"+
			"#@ ImagePlus imp\n"+
			"#@ float contrast_min\n"+
			"#@ float contrast_max\n"+
			"\n"+
			"# It is important that seeds (any non-zero pixels) are stored directly into the input 'imp' image!\n"+
			"\n"+
			"# The 'contrast_min' and 'contrast_max' report the current BDV display (contrast) setting\n"+
			"# used with the displayed input image (from which the 'imp' is cropped out). You may want\n"+
			"# (but need not) to consider this for the seeds extraction...\n"+
			"\n"+
			"from ij import IJ\n"+
			"\n"+
			"# It is possible to report from this script, it will appear in Fiji console.\n"+
			"print(\"contrast:\",contrast_min,contrast_max)\n"+
			"\n"+
			"# Example threshold function that thresholds _inplace_\n"+
			"def threshold(thres_val):\n"+
			"    pxs = imp.getProcessor().getPixels()\n"+
			"    for i in range(len(pxs)):\n"+
			"        pxs[i] = 1 if pxs[i] > thres_val else 0\n"+
			"\n"+
			"# Example of using a standard Fiji plugin \n"+
			"IJ.run(imp, \"Maximum...\", \"radius=1\")\n"+
			"threshold(0.65)\n"+
			"\n"+
			"# Don't use the updateAndRepaintWindow() in conjunction with BDV+SAMJ,\n"+
			"# but it is useful when running (debugging) this script directly from Fiji\n"+
			"# (e.g. on some of the debug crop-out that came from BDV+SAMJ).\n"+
			"#\n"+
			"# imp.updateAndRepaintWindow()";
	}
}
