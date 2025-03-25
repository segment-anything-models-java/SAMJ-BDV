package ai.nets.samj.gui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * When attached to a Component, the class monitors how many from the
 * basic English keyboard keys ('a' to 'z' keys) is currently pressed,
 * counting-in also the Shift and Ctrl modifier keys. If both Shift
 * keys are pressed, they are counted as one; similarly for Ctrl keys.
 */
public class KeyStrokesMonitor implements KeyListener {

	private int pressedKeysCounter = 0;

	public int getPressedKeysCnt() {
		return pressedKeysCounter;
	}

	/**
	 * @param keyCode Any keycode, but the method is sensible only for 'a'-'z','shift' and 'control'.
	 * @return Current state of the keyCode if the keyCode is supported, otherwise returns false.
	 */
	public boolean getPressedStateForKeyCode(int keyCode) {
		int index = keyCodeToIndex(keyCode);
		return index > -1 ? pressedKeys[index] : false;
	}

	@Override
	public void keyTyped(KeyEvent e) { /* empty */ }

	@Override
	public void keyPressed(KeyEvent e) {
		int index = keyCodeToIndex(e.getKeyCode());
		if (index > -1 && !pressedKeys[index]) {
			pressedKeys[index] = true;
			++pressedKeysCounter;
		}
		//System.out.println("Key pressed: "+e.getKeyCode()+" (counter="+pressedKeysCounter+")");
	}

	@Override
	public void keyReleased(KeyEvent e) {
		int index = keyCodeToIndex(e.getKeyCode());
		if (index > -1 && pressedKeys[index]) {
			pressedKeys[index] = false;
			--pressedKeysCounter;
		}
		//System.out.println("Key released: "+e.getKeyCode()+" (counter="+pressedKeysCounter+")");
	}

	protected int keyCodeToIndex(int keyCode) {
		if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) return keyCode - KeyEvent.VK_A;
		if (keyCode == KeyEvent.VK_SHIFT) return INDEX_SHIFT;
		if (keyCode == KeyEvent.VK_CONTROL) return INDEX_CTRL;
		return -1;
	}

	private final int INDEX_SHIFT = KeyEvent.VK_Z - KeyEvent.VK_A +1;
	private final int INDEX_CTRL = INDEX_SHIFT +1;
	private final boolean[] pressedKeys = new boolean[INDEX_CTRL +1];
}
