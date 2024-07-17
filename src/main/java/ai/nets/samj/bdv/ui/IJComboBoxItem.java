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
package ai.nets.samj.bdv.ui;


import ai.nets.samj.gui.components.ComboBoxItem;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

/**
 * Implementation of the SAMJ interface {@link ComboBoxItem} that provides the SAMJ GUI
 * an item for the combobox that references ImageJ {@link ImagePlus}
 * 
 * @author Carlos Garcia
 */
public class IJComboBoxItem extends ComboBoxItem {
	
	/**
	 * 
	 * Combobox item that contains an Object associated to a unique identifier.
	 * For ImageJ the object is an ImageJ {@link ImagePlus}
	 * @param uniqueID
	 * 	unique indentifier of the combobox element to avoid confusion when there are two images
	 * 	with the same name
	 * @param seq
	 * 	the object of interest, whihc in the case of ImageJ is and {@link ImagePlus}
	 */
	public IJComboBoxItem(int uniqueID, Object seq) {
		super(uniqueID, seq);
	}
	
	/**
	 * Create an empty {@link ComboBoxItem}. Its id is -1
	 */
	public IJComboBoxItem() {
		super();
	}

	@Override
	/**
	 * {@inheritDoc}
	 * 
	 * For ImageJ is the name of the ImageJ {@link ImagePlus}
	 */
	public String getImageName() {
		return ((ImagePlus) this.getValue()).getTitle();
	}

	@Override
	/**
	 * {@inheritDoc}
	 * 
	 * Convert the {@link ImagePlus} into a {@link RandomAccessibleInterval}
	 */
	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> getImageAsImgLib2() {
		Img<?> img = ImageJFunctions.wrap((ImagePlus) this.getValue());
		return Cast.unchecked(img);
		//return Cast.unchecked(Views.permute(img, 1, 2));
	}


}
