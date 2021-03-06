/**
 * New BSD License
 * http://www.opensource.org/licenses/bsd-license.php
 * Copyright 2009-2016 RaptorProject (https://github.com/Raptor-Fics-Interface/Raptor)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the RaptorProject nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package raptor.swt.chess.analysis;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import org.apache.commons.lang.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import raptor.Raptor;
import raptor.chess.Game;
import raptor.chess.Move;
import raptor.chess.Variant;
import raptor.chess.util.GameUtils;
import raptor.engine.uci.UCIBestMove;
import raptor.engine.uci.UCIEngine;
import raptor.engine.uci.UCIInfo;
import raptor.engine.uci.UCIInfoListener;
import raptor.engine.uci.UCIMove;
import raptor.engine.uci.info.BestLineFoundInfo;
import raptor.engine.uci.info.DepthInfo;
import raptor.engine.uci.info.MultiPV;
import raptor.engine.uci.info.NodesPerSecondInfo;
import raptor.engine.uci.info.ScoreInfo;
import raptor.engine.uci.info.TimeInfo;
import raptor.engine.uci.options.UCICheck;
import raptor.international.L10n;
import raptor.pref.PreferenceKeys;
import raptor.service.ThreadService;
import raptor.service.UCIEngineService;
import raptor.swt.RaptorTable;
import raptor.swt.RaptorTable.RaptorTableAdapter;
import raptor.swt.chess.ChessBoardController;
import raptor.swt.chess.EngineAnalysisWidget;
import raptor.util.RaptorLogger;
import raptor.util.RaptorRunnable;

public class UciAnalysisWidget implements EngineAnalysisWidget {
	private static final RaptorLogger LOG = RaptorLogger.getLog(UciAnalysisWidget.class);
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("###,###,###,###,###,###,###,###,###");

	protected ChessBoardController controller;
	protected Composite composite, topLine;
	protected UCIEngine engine;
	protected Label depthHeaderLabel;
	protected Label depthLabel;
	protected Label timeHeaderLabel;
	protected Label timeLabel;
	protected Label notesHeaderLabel;
	protected Label nodesLabel;
	protected Label bestMoveHeaderLabel;
	protected Label bestMoveLabel;
	protected Combo engineCombo;
	protected RaptorTable bestMoves;
	protected Button startStopButton;
	protected boolean ignoreUciInfo = false;
	protected static L10n local = L10n.getInstance();
	protected Object engineLock = new Object();

	protected UCIInfoListener listener = new UCIInfoListener() {
		public void engineSentBestMove(UCIBestMove uciBestMove) {
		}

		public void engineSentInfo(final UCIInfo[] infos) {
			if (engine.isConnected() && !ignoreUciInfo) {
				Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable(controller.getConnector()) {
					@Override
					public void execute() {
						int multiPv = -1;
						String score = null;
						String time = null;
						String depth = null;
						String nps = null;
						String pv = null;
						String bestMove = null;

						for (UCIInfo info : infos) {
							if (info instanceof ScoreInfo) {
								ScoreInfo scoreInfo = (ScoreInfo) info;
								if (((ScoreInfo) info).getMateInMoves() != 0) {
									score = "Mate in " + scoreInfo.getMateInMoves();
								} else if (scoreInfo.isLowerBoundScore()) {
									score = "Calibrating";
								} else if (scoreInfo.isUpperBoundScore()) {
									score = "Calibrating";
								} else {
									double scoreAsDouble = controller.getGame().isWhitesMove()
											|| !engine.isMultiplyBlackScoreByMinus1()
													? scoreInfo.getValueInCentipawns() / 100.0
													: -scoreInfo.getValueInCentipawns() / 100.0;

									score = "" + new BigDecimal(scoreAsDouble).setScale(2, BigDecimal.ROUND_HALF_UP)
											.toString();
								}
							} else if (info instanceof DepthInfo) {
								DepthInfo depthInfo = (DepthInfo) info;
								depth = "" + depthInfo.getSearchDepthPlies();
							} else if (info instanceof NodesPerSecondInfo) {
								NodesPerSecondInfo nodesPerSecInfo = (NodesPerSecondInfo) info;
								nps = DECIMAL_FORMAT.format(nodesPerSecInfo.getNodesPerSecond());
							} else if (info instanceof TimeInfo) {
								TimeInfo timeInfo = (TimeInfo) info;
								time = new BigDecimal(timeInfo.getTimeMillis() / 1000.0)
										.setScale(1, BigDecimal.ROUND_HALF_UP).toString();
							} else if (info instanceof BestLineFoundInfo) {
								BestLineFoundInfo bestLineFoundInfo = (BestLineFoundInfo) info;
								StringBuilder line = new StringBuilder(100);
								Game gameClone = controller.getGame().deepCopy(true);
								gameClone.addState(Game.UPDATING_SAN_STATE);
								gameClone.clearState(Game.UPDATING_ECO_HEADERS_STATE);

								boolean isFirstMove = true;

								for (UCIMove move : bestLineFoundInfo.getMoves()) {
									try {
										Move gameMove = null;

										if (move.isPromotion()) {
											gameMove = gameClone.makeMove(move.getStartSquare(), move.getEndSquare(),
													move.getPromotedPiece());
										} else {
											gameMove = gameClone.makeMove(move.getStartSquare(), move.getEndSquare());
										}

										String san = GameUtils.convertSanToUseUnicode(gameMove.getSan(),
												gameMove.isWhitesMove());
										String moveNumber = isFirstMove && !gameMove.isWhitesMove()
												? gameMove.getFullMoveCount() + ") ... "
												: gameMove.isWhitesMove() ? gameMove.getFullMoveCount() + ") " : "";
										line.append((line.equals("") ? "" : " ") + moveNumber + san
												+ (gameClone.isInCheck() ? "+" : "")
												+ (gameClone.isCheckmate() ? "#" : ""));
										if (isFirstMove) {
											bestMove = moveNumber + san + (gameClone.isInCheck() ? "+" : "")
													+ (gameClone.isCheckmate() ? "#" : "");
										}
										isFirstMove = false;
									} catch (Throwable t) {
										if (LOG.isInfoEnabled()) {
											LOG.info(
													"Illegal line found skipping line (This can occur if the position was "
															+ "changing when the analysis line was being calculated).",
													t);
										}
										break;
									}
								}
								pv = line.toString();
							} else if (info instanceof MultiPV) {
								MultiPV multiPvInfo = (MultiPV) info;
								multiPv = multiPvInfo.getId();
							}
						}

						if (!ignoreUciInfo && score != null && multiPv != -1) {
							final String finalScore = score;
							final String finalTime = time;
							final String finalDepth = depth;
							final String finalNodes = nps;
							final String finalPV = pv;
							final String finalBestMove = bestMove;
							final int finalMultiPv = multiPv;

							Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable(controller.getConnector()) {
								@Override
								public void execute() {
									if (composite.isDisposed()) {
										return;
									}

									if (bestMoves.getRowCount() == 0) {
										String[][] data = new String[UCIEngine.MULTI_PLY][6];
										for (int i = 0; i < data.length; i++)
											for (int j = 0; j < data[i].length; j++)
												data[i][j] = "";
										bestMoves.refreshTable(data);
									}

									int row = finalMultiPv - 1;

									if (StringUtils.isNotBlank(finalScore)) {
										bestMoves.setText(row, 0, finalScore);
									}
									if (StringUtils.isNotBlank(finalPV)) {
										bestMoves.setText(row, 1, finalPV);
									}
									if (StringUtils.isNotBlank(finalDepth)) {
										depthLabel.setText(finalDepth);
									}
									if (StringUtils.isNotBlank(finalTime)) {
										timeLabel.setText(finalTime);
									}
									if (StringUtils.isNotBlank(finalNodes)) {
										nodesLabel.setText(finalNodes);
									}
									if (row == 0 && StringUtils.isNotBlank(finalBestMove)) {
										bestMoveLabel.setText(finalBestMove);
									}

									topLine.layout();
								}
							});
						}
					}
				});
			}
		}
	};

	public void clear() {
		Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable(controller.getConnector()) {
			@Override
			public void execute() {
				bestMoves.clearTable();
			}
		});
	}

	public Composite create(Composite parent) {
		composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));

		composite.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if (engine != null) {
					ignoreUciInfo = true;
					engine.quit();
				}
			}
		});

		topLine = new Composite(composite, SWT.LEFT);
		topLine.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		RowLayout rowLayout = new RowLayout();
		rowLayout.marginBottom = 0;
		rowLayout.marginTop = 0;
		rowLayout.marginLeft = 5;
		rowLayout.marginRight = 5;
		rowLayout.marginHeight = 2;
		rowLayout.marginWidth = 2;
		rowLayout.spacing = 0;
		topLine.setLayout(rowLayout);

		engineCombo = new Combo(topLine, SWT.DROP_DOWN | SWT.READ_ONLY);
		engineCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
			}
		});

		startStopButton = new Button(topLine, SWT.FLAT);
		startStopButton.setText(local.getString("uciAnalW_31"));
		startStopButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (startStopButton.getText().equals(local.getString("uciAnalW_32"))) {
					start();
					startStopButton.setText(local.getString("uciAnalW_33"));
				} else {
					stop();
					startStopButton.setText(local.getString("uciAnalW_34"));
				}
			}
		});

		bestMoveHeaderLabel = new Label(topLine, SWT.LEFT);
		bestMoveHeaderLabel.setText("   " + local.getString("uciAnalBestMove"));

		bestMoveLabel = new Label(topLine, SWT.LEFT);
		bestMoveLabel.setText("");

		depthHeaderLabel = new Label(topLine, SWT.LEFT);
		depthHeaderLabel.setText("   " + local.getString("uciAnalDepth"));
		depthLabel = new Label(topLine, SWT.LEFT);
		depthLabel.setText("");

		notesHeaderLabel = new Label(topLine, SWT.LEFT);
		notesHeaderLabel.setText("   " + local.getString("uciAnalNodes"));

		nodesLabel = new Label(topLine, SWT.LEFT);
		nodesLabel.setText(local.getString("uciAnalNodes"));

		timeHeaderLabel = new Label(topLine, SWT.LEFT);
		timeHeaderLabel.setText("   " + local.getString("uciAnalTime"));

		timeLabel = new Label(topLine, SWT.LEFT);
		timeLabel.setText("");

		FontData fontData = depthHeaderLabel.getFont().getFontData()[0];
		Font headerFont = new Font(Raptor.getInstance().getDisplay(),
				new FontData(fontData.getName(), fontData.getHeight(), SWT.BOLD));
		depthHeaderLabel.setFont(headerFont);
		notesHeaderLabel.setFont(headerFont);
		timeHeaderLabel.setFont(headerFont);
		bestMoveHeaderLabel.setFont(headerFont);

		bestMoves = new RaptorTable(composite, SWT.BORDER | SWT.FULL_SELECTION, false, true);
		bestMoves.setToolTipText(local.getString("uciAnalW_37"));
		bestMoves.addColumn(local.getString("uciAnalW_38"), SWT.LEFT, 18, false, null);
		bestMoves.addColumn(local.getString("uciAnalW_42"), SWT.LEFT, 82, false, null);
		bestMoves.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		bestMoves.addRaptorTableListener(new RaptorTableAdapter() {
			@Override
			public void rowRightClicked(MouseEvent event, final String[] rowData) {
				Menu menu = new Menu(UciAnalysisWidget.this.composite.getShell(), SWT.POP_UP);
				MenuItem item = new MenuItem(menu, SWT.PUSH);
				item.setText(local.getString("uciAnalW_43"));
				item.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						Clipboard clipboard = new Clipboard(composite.getDisplay());
						String text = GameUtils.removeUnicodePieces(rowData[1]);
						TextTransfer textTransfer = TextTransfer.getInstance();
						Transfer[] transfers = new Transfer[] { textTransfer };
						Object[] data = new Object[] { text };
						clipboard.setContents(data, transfers);
						clipboard.dispose();
					}
				});
				menu.setVisible(true);
				while (!menu.isDisposed() && menu.isVisible()) {
					if (!composite.getDisplay().readAndDispatch()) {
						composite.getDisplay().sleep();
					}
				}
				menu.dispose();
			}
		});

		createEnginesCombo();

		updateFromPrefs();
		return composite;
	}

	public ChessBoardController getChessBoardController() {
		return controller;
	}

	public Composite getControl() {
		return composite;
	}

	public void onShow() {
		clear();
		start();
		composite.layout(true, true);
	}

	public void quit() {
		ignoreUciInfo = true;
		Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable(controller.getConnector()) {
			@Override
			public void execute() {
				if (!composite.isDisposed()) {
					clear();
				}
			}
		});
		if (engine != null) {
			ThreadService.getInstance().run(new Runnable() {
				public void run() {
					engine.quit();
				}
			});
		}
	}

	public void setController(ChessBoardController controller) {
		this.controller = controller;
	}

	public void stop() {
		if (engine != null) {
			ignoreUciInfo = true;
			ThreadService.getInstance().run(new Runnable() {
				public void run() {
					try {
						engine.quit();
					} catch (Throwable t) {
					}
					Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable() {
						@Override
						public void execute() {
							startStopButton.setText(local.getString("uciAnalW_44"));
						}
					});
				}
			});
		}
	}

	public void updateFromPrefs() {
		Color background = Raptor.getInstance().getPreferences().getColor(PreferenceKeys.BOARD_BACKGROUND_COLOR);
		Color labelForeground = Raptor.getInstance().getPreferences().getColor(PreferenceKeys.BOARD_CONTROL_COLOR);
		
		composite.setBackground(background);
		topLine.setBackground(background);
		bestMoveHeaderLabel.setForeground(labelForeground);
		bestMoveLabel.setForeground(labelForeground);
		depthHeaderLabel.setForeground(labelForeground);
		depthLabel.setForeground(labelForeground);
		notesHeaderLabel.setForeground(labelForeground);
		nodesLabel.setForeground(labelForeground);
		timeHeaderLabel.setForeground(labelForeground);
		timeLabel.setForeground(labelForeground);
	}

	public void updateToGame() {
		if (startStopButton.getText().equals(local.getString("uciAnalW_45"))) {
			start();
		}
	}

	public void start() {
		if (composite.isVisible()) {
			ThreadService.getInstance().run(new Runnable() {
				public void run() {
					if (LOG.isDebugEnabled()) {
						LOG.debug("In UciAnalysisWidget.start(" + engine.getUserName() + ")");
					}
					try {
						synchronized (engineLock) {
							try {
								engine.stop();
							} catch (Throwable t) {
							}

							if (!engine.isConnected()) {
								engine.connect();
							}

							if (controller.getGame().getVariant() == Variant.fischerRandom) {
								UCICheck opt = (UCICheck) engine.getOption("UCI_Chess960");
								opt.setValue("true");
								engine.setOption(opt);
							} else {
								UCICheck opt = (UCICheck) engine.getOption("UCI_Chess960");
								opt.setValue("false");
								engine.setOption(opt);
							}
							

							engine.newGame();
							engine.setPosition(controller.getGame().toFen(), null);
							engine.isReady();
							ignoreUciInfo = false;
							engine.go(engine.getGoAnalysisParameters(), listener);
							Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable() {
								@Override
								public void execute() {
									if (composite.isVisible()) {
										startStopButton.setText(local.getString("uciAnalW_54"));
										bestMoves.clearTable();
									}
								}
							});
						}
					} catch (Throwable t) {
						LOG.error("Error starting engine", t);
					} finally {
					}
				}
			});
		}
	}

	protected void createEnginesCombo() {

		engineCombo.removeAll();

		engine = UCIEngineService.getInstance().getEngine();

		engineCombo.add(engine.getUserName());

		engineCombo.select(0);

		topLine.pack(true);
		topLine.layout(true, true);
	}
}