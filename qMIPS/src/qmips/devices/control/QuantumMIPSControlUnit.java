package qmips.devices.control;


import javax.swing.JPanel;

import qmips.devices.Device;
import qmips.others.Behavior;
import qmips.others.Bus;

/**
 * 
 * Unidad de control del MIPS cuantico.
 * Es una maquina de estados que evoluciona con el reloj
 * segun las instrucciones que se reciban.
 * 
 * @author Jaime Coello de Portugal
 *
 */
public class QuantumMIPSControlUnit extends Device implements ControlUnit {

	Bus clk, rst, opcode;
	Bus machineNotify;
	Bus pcWriteCond, pcWrite, iOrD, memRead, memWrite, memToReg, irWrite,
			pcSource, aluOp, aluSrcB, aluSrcA, regWrite, regDst, solPCWrite,
			aluControl, target, qExe, aluOverf;
	State ife, id, mac, mar, maw, mrc, rew, exe, imm, rc, bc, jc, qt, qex, qmea, trap;
	State current, next;
	IControlUnitDisplay disp;
	int trapNum = -1;

	public QuantumMIPSControlUnit(Bus pcWriteCond, Bus pcWrite, Bus iOrD,
			Bus memRead, Bus memWrite, Bus memToReg, Bus irWrite, Bus pcSource,
			Bus aluOp, Bus aluSrcB, Bus aluSrcA, Bus regWrite, Bus regDst,
			Bus solPCWrite, Bus aluControl, Bus target, Bus qExe, Bus aluOverf, Bus opcode, Bus clk, Bus rst) {
		this.pcWriteCond = pcWriteCond;
		this.pcWrite = pcWrite; 
		this.iOrD = iOrD;
		this.memRead = memRead; 
		this.memWrite = memWrite; 
		this.memToReg = memToReg; 
		this.irWrite = irWrite;
		this.pcSource = pcSource; 
		this.aluOp = aluOp; 
		this.aluSrcB = aluSrcB; 
		this.aluSrcA = aluSrcA; 
		this.regWrite = regWrite; 
		this.regDst = regDst; 
		this.solPCWrite = solPCWrite;
		this.aluControl = aluControl; 
		this.target = target;
		this.qExe = qExe; 
		this.aluOverf = aluOverf;
		this.clk = clk;
		this.rst = rst;
		this.opcode = opcode;
		this.machineNotify = new Bus(1);
		disp = new ControlUnitDisplay();
		defineStates();
		defineBehavior();
	}

	@Override
	protected void defineBehavior() {

		behavior(new Bus[] { clk }, new Behavior() {

			@Override
			public void task() {
				 if (clk.read().get(0)) {
					if(next == null){
						current = ife;
					}else{
						current = next;
					}
					current.setTransition();
					machineNotify.write(1,1);
				}
			}

		});
		
		behavior(new Bus[]{ rst }, new Behavior() {
			
			@Override
			public void task() {
				if (rst.read().get(0)) {
					current = ife;
					disp.setState("IF", "Instruction fetch");
					trapNum = -1;
					current.setOutput();
				}
			}
		});

		behavior(new Bus[] { opcode, machineNotify }, new Behavior() {

			@Override
			public void task() {
				current.setTransition();
				current.setOutput();
			}

		});
		
		behavior(new Bus[]{aluOverf}, new Behavior(){

			@Override
			public void task() {
				if(aluOverf.read().get(0)){
					trapNum = -2;
					next = trap;
				}
			}
			
		});
	}

	/**
	 * 
	 * Aqui se definen los estados por los que puede pasar la unidad de
	 * control. De cada estado se debe describir que salida genera en los
	 * buses y a que estado evoluciona.
	 * 
	 */
	public void defineStates() {
		ife = new State() {

			@Override
			public void setOutput() {
				memRead.write(1,1);
				aluSrcA.write(0,1);
				iOrD.write(0,1);
				irWrite.write(1,1);
				aluSrcB.write(1,2);
				aluOp.write(0,2);
				pcWrite.write(1,1);
				pcSource.write(0,2);
				
				regWrite.write(0,1);
				pcWriteCond.write(0, 1);
				memWrite.write(0, 1);
				qExe.write(0, 1);
				target.write(0, 1);
				disp.setState("IF", "Instruction fetch");
			}

			@Override
			public void setTransition() {
				next =  id;
			}

		};
		
		id = new State(){

			@Override
			public void setOutput() {
				aluSrcA.write(0, 1);
				aluSrcB.write(3, 2);
				aluOp.write(0, 2);
				
				memRead.write(0,1);
				irWrite.write(0,1);
				pcWrite.write(0,1);
				disp.setState("ID", "Instruction decode");
			}

			@Override
			public void setTransition() {
				switch(opcode.read().toInteger()){
				case 0x23: //LW
				case 0x2B: //SW
					next = mac;
					break;
				case 0: //R-Type
					next = exe;
					break;
				case 0x5: //BNE
				case 0x4: //BEQ
					next = bc;
					break;
				case 0x2: //J
					next = jc;
					break;
				case 0x8: //ADDI
					next = imm;
					break;
				case 0xC: //Q-Type
				case 0xD: //Q-Meas
					next = qt;
					break;
				case 0x1A: //TRAP
					trapNum = 0;
					next = trap;
					break;
				}
			}
			
		};
		
		mac = new State(){

			@Override
			public void setOutput() {
				aluSrcA.write(1, 1);
				aluSrcB.write(2, 2);
				aluOp.write(0, 2);
				disp.setState("MAC", "Memory address calculation");
			}

			@Override
			public void setTransition() {
				int intOp = opcode.read().toInteger();
				if(intOp == 0x23){
					next = mar;
				}else{
					next = maw;
				}
			}
			
		};
		
		mar = new State(){

			@Override
			public void setOutput() {
				memRead.write(1,1);
				iOrD.write(1, 1);
				disp.setState("MAR", "Memory read");
			}

			@Override
			public void setTransition() {
				next = mrc;
			}
			
		};
		
		maw = new State(){

			@Override
			public void setOutput() {
				memWrite.write(1,1);
				iOrD.write(1, 1);
				disp.setState("MAW", "Memory write");
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
		
		mrc = new State(){

			@Override
			public void setOutput() {
				regDst.write(0, 1);
				regWrite.write(1, 1);
				memToReg.write(1, 2);
				
				memRead.write(0, 1);
				disp.setState("MCR", "Memory to register");
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
		
		rew = new State(){

			@Override
			public void setOutput() {
				regDst.write(0, 1);
				regWrite.write(1, 1);
				memToReg.write(0, 2);
				
				memRead.write(0, 1);
				disp.setState("REW", "Register write");
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
		
		exe = new State(){

			@Override
			public void setOutput() {
				aluSrcA.write(1, 1);
				aluSrcB.write(0, 2);
				aluOp.write(2, 2);
				disp.setState("EXE", "Execution");
			}

			@Override
			public void setTransition() {
				next = rc;
			}
			
		};
		
		imm = new State(){

			@Override
			public void setOutput() {
				aluSrcA.write(1, 1);
				aluSrcB.write(2, 2);
				aluOp.write(0, 1);
				disp.setState("IMM", "Immediate execution");
			}

			@Override
			public void setTransition() {
				next = rew;
			}
			
		};
		
		rc = new State(){

			@Override
			public void setOutput() {
				regDst.write(1, 1);
				regWrite.write(1, 1);
				memToReg.write(0, 2);
				disp.setState("RC", "Register write");
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
		
		bc = new State(){

			@Override
			public void setOutput() {
				aluSrcA.write(1, 1);
				aluSrcB.write(0, 2);
				aluOp.write(1, 2);
				pcWriteCond.write(1, 1);
				pcSource.write(1, 2);
				disp.setState("BC", "Branch completion");
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
		
		jc = new State(){

			@Override
			public void setOutput() {
				pcWrite.write(1, 1);
				pcSource.write(2, 2);
				disp.setState("JC", "Jump completion");
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
		
		qt = new State(){

			@Override
			public void setOutput() {
				target.write(1, 1);
				disp.setState("QT", "Quantum target");
			}

			@Override
			public void setTransition() {
				next = qex;
			}
			
		};
		
		qex = new State(){

			@Override
			public void setOutput() {
				qExe.write(1, 1);
				
				target.write(0, 1);
				disp.setState("QEX", "Quantum execution");
			}

			@Override
			public void setTransition() {
				switch(opcode.read().toInteger()){
				case 0xC:
					next = ife;
					break;
				case 0xD:
					next = qmea;
					break;
				}
			}
			
		};
		
		qmea = new State(){

			@Override
			public void setOutput() {
				memToReg.write(3, 2);
				regWrite.write(1, 1);
				regDst.write(1, 1);
				
				qExe.write(0, 1);
				disp.setState("QMEA", "Quantum measuremente write");
			}

			@Override
			public void setTransition() {
					next = ife;
			}
			
		};
		
		trap = new State(){

			@Override
			public void setOutput() {
				disp.setState("TRAP", "Exception " + trapNum);
			}

			@Override
			public void setTransition() {
				next = ife;
			}
			
		};
	}

	interface State {

		void setOutput();

		void setTransition();

	}
	
	@Override
	public JPanel display(){
		return (JPanel)disp;
	}


	/**
	 * 
	 * Metodo ofrecido por la unidad de control que la interfaz
	 * de usuario utilizara para saber si se ha producido una
	 * excepcion.
	 * 
	 */
	@Override
	public int checkTrap() {
		return trapNum;
	}

}