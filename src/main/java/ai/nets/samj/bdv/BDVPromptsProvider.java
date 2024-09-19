/*-
 * #%L
 * Plugin to help image annotation with SAM-based Deep Learning models
 * %%
 * Copyright (C) 2024 SAMJ developers.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ai.nets.samj.bdv;

import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.gui.components.ComboBoxItem;
import ai.nets.samj.ui.PromptsResultsDisplay;
import ai.nets.samj.ui.SAMJLogger;
import ai.nets.samj.ui.UtilityMethods;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;

import java.io.File;
import java.awt.Polygon;
import java.util.Collections;
import java.util.List;

import java.util.function.Consumer;
import ai.nets.samj.bdv.polygons.PlanarPolygonIn3D;

public class BDVPromptsProvider <T extends RealType<T> & NativeType<T>>
implements PromptsResultsDisplay, UtilityMethods {

	public BDVPromptsProvider(final Img<T> image, final String imageName, final SAMJLogger logger) {
		this.image = image;
		this.logger = logger;
		this.bdv = new SAMJ_BDV<>(image, imageName);
	}

	final private SAMJ_BDV<T> bdv;
	private final Img<T> image;
	private final SAMJLogger logger;

	@Override
	public List<ComboBoxItem> getListOfOpenImages() {
		logger.info("getListOfOpenImages");
		return NO_AVAILABLE_ANNOTATIONS_SITE_COMBOBOX_LIST;
	}

	static final Img<FloatType> fakeSmallImage = ArrayImgs.floats(256,256);
	static final List<ComboBoxItem> NO_AVAILABLE_ANNOTATIONS_SITE_COMBOBOX_LIST =
		Collections.singletonList( new ComboBoxItem(0, fakeSmallImage) {
			@Override
			public String getImageName() { return "Currently there's no active annotation site inside BDV"; }
			@Override
			public RandomAccessibleInterval<FloatType> getImageAsImgLib2() { return fakeSmallImage; }
		} );

	@Override
	public Object getFocusedImage() {
		logger.warn("getFocusedImage");
		return bdv.getImageFromTheCurrentAnnotationSite();
	}

	@Override
	public void setRectIconConsumer(BooleanConsumer consumer) {
		logger.warn("setRectIconConsumer");
		consumer.accept(true);
	}

	@Override
	public void setPointsIconConsumer(BooleanConsumer consumer) {
		logger.warn("setPointsIconConsumer");
		//in BDV we ain't supporting points ATM
		consumer.accept(false);
	}

	@Override
	public void setFreelineIconConsumer(BooleanConsumer consumer) {
		logger.warn("setFreelineIconConsumer");
		//in BDV we ain't supporting free line drawing ATM
		consumer.accept(false);
	}

	@Override
	public RandomAccessibleInterval<?> giveProcessedSubImage(SAMModel selectedModel) {
		logger.warn("giveProcessedSubImage");
		return bdv.getImageFromTheCurrentAnnotationSite();
	}

	@Override
	public void switchToThisNet(SAMModel promptsToNetAdapter) {
		logger.warn("switchToThisNet");
		bdv.startUsingThisSAMModel(promptsToNetAdapter);
	}

	@Override
	public void notifyNetToClose() {
		//this comes when the SAMJ GUI lost connection (disengaged) any SAM network
		logger.warn("notifyNetToClose");
		bdv.stopCommunicatingToSAMModel();
		//or, bdv.close(); //TODO depends on when this is executed
	}

	@Override
	public List<Polygon> getPolygonsFromRoiManager() {
		logger.warn("getPolygonsFromRoiManager");
		return Collections.emptyList(); //bdv.getAllPolygonsFromTheCurrentAnnotationSite();
	}

	@Override
	public void exportImageLabeling() {
		logger.warn("exportImageLabeling");
	}

	@Override
	public void improveExistingMask(File mask) {
		logger.warn("improveExistingMask");
	}

	@Override
	public void enableAddingToRoiManager(boolean shouldBeAdding) {
		logger.warn("enableAddingToRoiManager");
	}

	@Override
	public boolean isAddingToRoiManager() {
		logger.warn("isAddingToRoiManager");
		return true;
	}

	@Override
	public void switchToUsingRectangles() {
		logger.warn("switchToUsingRectangles");
		bdv.enablePrompts();
	}

	@Override
	public void switchToUsingBrush() {
		logger.warn("switchToUsingBrush");
		//NB: should not happen as we provide _false_ in setFreelineIconConsumer()
		bdv.disablePrompts();
	}

	@Override
	public void switchToUsingPoints() {
		logger.warn("switchToUsingPoints");
		//NB: should not happen as we provide _false_ in setPointsIconConsumer()
		bdv.disablePrompts();
	}

	@Override
	public void switchToNone() {
		//this comes when a new annotation site is selected in the SAMJ GUI,
		//corresponds to "unclicking" any of the three prompts buttons (rect, line, brush)
		logger.warn("switchToNone");
		bdv.disablePrompts();
	}

	@Override
	public void notifyException(SAMJException type, Exception ex) {
		logger.error("EXCEPTION CAPTURED: "+type.toString());
		logger.error("EXCEPTION CAPTURED: "+ex.getMessage());
		bdv.showMessage(ex.getMessage());
	}

	@Override
	public SAMModel getNetBeingUsed() {
		logger.warn("getNetBeingUsed");
		return null;
	}


	// ======================== API for plugin ========================
	public void showNewAnnotationSitesImages(boolean newState) {
		bdv.showNewAnnotationSitesImages = newState;
	}

	public void fakeResults(boolean newState) {
		bdv.fakeResults = newState;
	}

	public void newPolygonsConsumer(final Consumer<PlanarPolygonIn3D> consumer) {
		bdv.addPolygonsConsumer(consumer);
	}
}
