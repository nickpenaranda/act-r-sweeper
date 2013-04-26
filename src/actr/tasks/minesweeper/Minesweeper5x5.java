package actr.tasks.minesweeper;

import java.awt.Color;
import java.util.Date;
import java.util.Random;

import actr.model.Symbol;
import actr.task.*;

public class Minesweeper5x5 extends actr.task.Task {
	private static final int FIELD_W = 8;
	private static final int FIELD_H = 8;
	private static final int BUTTON_W = 48;
	private static final int BUTTON_H = 48;
	private static final int NUM_MINES = 4;
	private static final String[] slots = {
		"aa","ab","ac","ad","ae",
		"ba","bb","bc","bd","be",
		"ca","cb","cc","cd","ce",
		"da","db","dc","dd","de",
		"ea","eb","ec","ed","ee"};

	private Tile[][] field;
	private boolean[][] traversed;
	
	private Random rng;
	private int numMarks = 0;
	private int gameCount = 0;
	
	public Minesweeper5x5() {
		super();

		rng = new Random(new Date().getTime());
		
		// Allocate tile-field
		field = new Tile[FIELD_W][FIELD_H];
		traversed = new boolean[FIELD_W][FIELD_H];
		
		for(int x=0;x<FIELD_W;x++) {
			for(int y=0;y<FIELD_H;y++) {
				field[x][y] = new Tile(Tile.CHAR_UNEXPLORED,x*BUTTON_W,y*BUTTON_H,BUTTON_W,BUTTON_H);
				this.add(field[x][y]);
			}
		}
	}
	
	private void incrementAdjacent(int x, int y) {
		for(int ix=x-1;ix<=x+1;ix++) {
			for(int iy=y-1;iy<=y+1;iy++) {
				if(!(ix == x && iy == y)) {
					if(inBounds(ix,iy)) {
						field[ix][iy].incAdjacent();
					}
				}
			}
		}
	}

	private boolean inBounds(int x, int y) {
		return(x >= 0 && y >= 0 && x < FIELD_W && y < FIELD_H);
	}
	
	public void start ()
	{
		// Clear field
		for(int x=0;x<FIELD_W;x++) {
			for(int y=0;y<FIELD_H;y++) {
				field[x][y].clearAllState();
				field[x][y].setBackground(Color.GRAY);
			}
		}
		
		// Place mines
		for(int i=0;i<NUM_MINES;i++) {
			int x,y;
			do {
				x = rng.nextInt(FIELD_W);
				y = rng.nextInt(FIELD_H);
			} while(field[x][y].hasState(Tile.HASMINE));
			field[x][y].setFlag(Tile.HASMINE);
			incrementAdjacent(x,y);
		}

		// Clear view
		for(int x=0;x<FIELD_W;x++) {
			for(int y=0;y<FIELD_H;y++) {
				field[x][y].setText(Tile.CHAR_UNEXPLORED);
			}
		}
		
		numMarks = 0;
		processDisplay();
	}
		
	@Override
	public void typeKey(char c) {
		int x = (int)(this.getMouseX() / BUTTON_W);
		int y = (int)(this.getMouseY() / BUTTON_H);
		
		switch(c) {
		case 's':
			doScan(x,y);
			break;
		case 'c':
			clickTile(x,y);
			break;
		case 'f':
			markTile(x,y);
			break;
		}
		this.getModel().getVision().clearVisual();
		processDisplay();
	}

	private void doScan(int x, int y) {
		int i = 0;
		for(int iy=-2;iy<=2;iy++) {
			for(int ix=-2;ix<=2;ix++) {
				if(!inBounds(x+ix, y+iy))
					this.getModel().getBuffers().setSlot(Symbol.goal, Symbol.get(slots[i]), Symbol.get("x"));
				else 
					this.getModel().getBuffers().setSlot(Symbol.goal, Symbol.get(slots[i]), Symbol.get(field[x+ix][y+iy].getText()));
				i++;
			}
		}
	}

	private void markTile(int x, int y) {
		field[x][y].setFlag(Tile.MARKED);
		field[x][y].setText(Tile.CHAR_FLAG);
		field[x][y].setBackground(Color.ORANGE);
		
		/* This should help models learn quicker */
		if(!field[x][y].hasState(Tile.HASMINE)) {
			this.getModel().getBuffers().setSlot(Symbol.goal, Symbol.get("state"), Symbol.get("gameover-loss"));
			printLoseStats();
			this.start();
			return;
		}
		
		boolean win = true;
		for(int ix=0;ix<FIELD_W;ix++) {
			for(int iy=0;iy<FIELD_H;iy++) {
				if(field[ix][iy].hasState(Tile.HASMINE) && !field[ix][iy].hasState(Tile.MARKED)) {
					win = false;
					break;
				}
			}
			if(!win) break;
		}
		if(numMarks == NUM_MINES && !win) {
			this.getModel().getBuffers().setSlot(Symbol.goal, Symbol.get("state"), Symbol.get("gameover-loss"));
			printLoseStats();
			this.start();
		} else if(win) { // All mines have been marked!
			int numChunks = this.getModel().getDeclarative().size();
			this.getModel().getBuffers().setSlot(Symbol.goal, Symbol.get("state"), Symbol.get("gameover-win"));
			String s = String.format("Game #%05d *** YOU WIN!!! *** (%d chunks in DM)",++gameCount,numChunks);
			this.getModel().output(s);
			this.start();
		}
	} 

	private void clearTraversed() {
		for(int x=0;x<FIELD_W;x++) {
			for(int y=0;y<FIELD_H;y++) {
				traversed[x][y] = false;
			}
		}
	}
	
	private void clickTile(int x,int y) {
		if(field[x][y].hasState(Tile.HASMINE)) {
			field[x][y].setText(Tile.CHAR_MINE);
			this.getModel().getBuffers().setSlot(Symbol.goal, Symbol.get("state"), Symbol.get("gameover-loss"));
			printLoseStats();
			this.start();
		}
		else {
			clearTraversed();
			checkTile(x,y);
		}
	}
	
	private void printLoseStats() {
		int numCorrectlyMarked = 0;
		int numIncorrectlyMarked = 0;
		int numUnmarkedMine = 0;
		for(int x=0;x<FIELD_W;x++) {
			for(int y=0;y<FIELD_H;y++) {
				if(field[x][y].hasState(Tile.HASMINE)) {
					if(field[x][y].hasState(Tile.MARKED))
						numCorrectlyMarked++;
					else
						numUnmarkedMine++;
				} else if(field[x][y].hasState(Tile.MARKED)){
					numIncorrectlyMarked++;
				}
			}
		}
		int numChunks = this.getModel().getDeclarative().size();
		String s = String.format("Game #%05d *** YOU LOSE: %d correct, %d incorrect, %d unmarked *** (%d chunks in DM)",++gameCount,numCorrectlyMarked,numIncorrectlyMarked,numUnmarkedMine,numChunks);
		this.getModel().output(s);
	}
	
	private void checkTile(int x,int y) {
		if(x < 0 || y < 0 || x >= FIELD_W || y >= FIELD_H || traversed[x][y]) return;
		traversed[x][y] = true;
		
		if(field[x][y].hasState(Tile.HASMINE)) return;
		
		int numAdj = field[x][y].getAdjacent();
		if(numAdj > 0) {
			field[x][y].setText("" + numAdj);
			field[x][y].setBackground(Color.YELLOW);
			field[x][y].setFlag(Tile.VISIBLE);
			return;
		}
		else {
			field[x][y].setText(Tile.CHAR_CLEAR);
			field[x][y].setBackground(Color.GREEN);
			field[x][y].setFlag(Tile.VISIBLE);
		}
		
		checkTile(x-1,y-1);
		checkTile(x-1,y);
		checkTile(x-1,y+1);
		checkTile(x,y-1);
		checkTile(x,y+1);
		checkTile(x+1,y-1);
		checkTile(x+1,y);
		checkTile(x+1,y+1);
	}
	
	private class Tile extends TaskButton {
		public static final String CHAR_UNEXPLORED = "u";
		public static final String CHAR_MINE = "m";
		public static final String CHAR_CLEAR = "c";
		public static final String CHAR_FLAG = "f";
		
		public static final int HASMINE = 1;
		public static final int VISIBLE = 2;
		public static final int MARKED = 4;
		
		private int tx,ty,tflags,numAdjacent;
		
		public Tile(String text, int x, int y, int width, int height) {
			super(text, x, y, width, height);
			
			this.tx = x / BUTTON_W;
			this.ty = y / BUTTON_H;
			this.tflags = 0;
			this.numAdjacent = 0;
		}
				
		public int getTX() { return(tx); }
		public int getTY() { return(ty); }
		public int getAdjacent() { return(numAdjacent); }
		public void incAdjacent() { numAdjacent++; }
		public boolean hasState(int flag) { return((tflags & flag) == flag);	}
		public void setFlag(int flag) { tflags = tflags | flag; }
		public void clearFlag(int flag) { tflags = tflags & ~flag; }
		public void clearAllState() { tflags = 0; numAdjacent = 0; }
	}
}
