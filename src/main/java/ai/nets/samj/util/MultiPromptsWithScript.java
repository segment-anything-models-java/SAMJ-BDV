package ai.nets.samj.util;

import bdv.interactive.prompts.BdvPrompts;
import bdv.tools.brightness.ConverterSetup;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.module.Module;
import org.scijava.module.ModuleService;
import org.scijava.script.ScriptService;
import java.io.File;

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
			ImageJFunctions.show(extImg.floatTypeImg, Prompts.getDebugImagesCounter() + ": source shared image");
		}

		try {
			final Module module = moduleService.createModule( scriptService.getScript(scriptFile) );
			module.setInput("imp", extImg.floatImagePlus);
			//TODO pass min,max setting to the script, create example script to the GUI
			System.out.println("==> Executing external script: "+scriptFile);
			module.run();
			System.out.println("==> External script finished now.");

			if ((bitFieldForRequestedDebugImages & Prompts.SHOW_THRESHOLDED_DBGIMAGE) > 0) {
				ImageJFunctions.show(extImg.floatTypeImg, Prompts.getDebugImagesCounter() + ": seeds in shared image");
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
}
