package ai.nets.samj.util;

import bdv.interactive.prompts.BdvPrompts;
import bdv.tools.brightness.ConverterSetup;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.script.ScriptService;

import java.io.File;
import java.io.FileNotFoundException;
import javax.script.ScriptException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MultiPromptsWithScript <T extends RealType<T> & NativeType<T>> implements BdvPrompts.SeedsFromPromptCreator<T> {

	final ScriptService scriptService;
	File scriptFile;
	int scriptWaitingTimeMins = 1;

	public MultiPromptsWithScript(final ScriptService scriptService, final File scriptFile) {
		if (scriptService == null || scriptFile == null) {
			throw new IllegalArgumentException("Both service and file path must be non-null!");
		}

		this.scriptService = scriptService;
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
			ImageJFunctions.show(inputImageToEstablishSeedsHere, "X: source original image"); //TODO: counter to image titles
		}
		if ((bitFieldForRequestedDebugImages & Prompts.SHOW_SOURCE_DBGIMAGE) > 0) {
			ImageJFunctions.show(extImg.floatTypeImg, "X: source shared image");
		}
		try {
			scriptService
					.run(scriptFile, true, "imp", extImg.floatImagePlus)
					.get(1, TimeUnit.MINUTES);
			System.out.println("SCRIPT FINISHED HAPPILY");

			if ((bitFieldForRequestedDebugImages & Prompts.SHOW_THRESHOLDED_DBGIMAGE) > 0) {
				ImageJFunctions.show(extImg.floatTypeImg, "X: seeds in shared image"); //TODO: counter to image titles
			}
		} catch (FileNotFoundException|ScriptException|TimeoutException|ExecutionException|InterruptedException e) {
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
