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

import ai.nets.samj.bdv.ui.BDVPromptsProvider;
import ai.nets.samj.communication.model.SAMModels;
import ai.nets.samj.gui.SAMJDialog;
import ai.nets.samj.ui.SAMJLogger;
import ai.nets.samj.bdv.ui.IJSamMethods;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import sc.fiji.simplifiedio.SimplifiedIO;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Vladimir Ulman
 */
public class SAMJ_BDV_Annotator {

	//TODO: maybe RAI would be enough
	public <T extends RealType<T>> void startBdvAnnotation(final Img<T> imageToBeAnnotated) {
		try {
			//get list of recognized installations of SAM(s)
			final SAMModels availableModels = new SAMModels();

			// TODO I (Carlos) don't know how to develop in IJ2 Logger guiSublogger = log.subLogger("PromptsResults window");
			SAMJLogger guilogger = new SAMJLogger() {
				@Override
				public void info(String text) {System.out.println(text);}
				@Override
				public void warn(String text) {System.out.println(text);}
				@Override
				public void error(String text) {System.out.println(text);}
			};
			// TODO I (Carlos) don't know how to develop in IJ2 Logger networkSublogger = log.subLogger("Networks window");
			SAMJLogger networkLogger = new SAMJLogger() {
				@Override
				public void info(String text) {System.out.println("network -- " + text);}
				@Override
				public void warn(String text) {System.out.println("network -- " + text);}
				@Override
				public void error(String text) {System.out.println("network -- " + text);}
			};
			SAMJLogger bdvLogger = new SAMJLogger() {
				@Override
				public void info(String text) {System.out.println("BDV -- " + text);}
				@Override
				public void warn(String text) {System.out.println("BDV -- " + text);}
				@Override
				public void error(String text) {System.out.println("BDV -- " + text);}
			};

			SAMJDialog samjDialog = new SAMJDialog( availableModels, new IJSamMethods(), guilogger, networkLogger);
			//create the GUI (BDV on the given image) adapter between the user inputs/prompts and SAMJ outputs
			samjDialog.setPromptsProvider( (imgAsObject) -> new BDVPromptsProvider(imageToBeAnnotated, bdvLogger) );

			JDialog dialog = new JDialog(new JFrame(), "SAMJ BDV Annotator");
			dialog.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					samjDialog.close();
				}
			});
			dialog.add(samjDialog);
			dialog.pack();
			dialog.setResizable(false);
			dialog.setModal(false);
			dialog.setVisible(true);

			//TODO, on BDV close call: samjDialog.close();
		} catch (RuntimeException e) {
			System.out.println("SAMJ BDV error: "+e.getMessage());
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		//grab some image
		ImgPlus image = SimplifiedIO.openImage("/home/ulman/devel/HackBrno23/HackBrno23_introIntoImglib2AndBDV__SOLUTION/src/main/resources/t1-head.tif");
		new SAMJ_BDV_Annotator().startBdvAnnotation(image);
	}
}
