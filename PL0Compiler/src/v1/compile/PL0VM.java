package v1.compile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import v1.define.Pcode;

/**
 * 能够解释执行PL/0所生成代码的虚拟机
 * 
 * @author 87663
 *
 */
public class PL0VM {
	
	private final static int	DEFAULT_STACK_SIZE	= 1000;//默认栈大小为1000
	
	private int[]				stack;//程序栈
	
	private List<Pcode>			pcodes;//执行的代码
	
	private Scanner				input;//输入流
	
	private PrintStream			output;//输出流
	
	public PL0VM(int stackSize, List<Pcode> pcodes, InputStream in, OutputStream out) {
		super();
		this.stack	= new int[stackSize];
		this.pcodes	= pcodes;
		this.input	= new Scanner(in);
		this.output	= new PrintStream(out);
	}
	
	public PL0VM(List<Pcode> pcodes) {
		super();
		this.stack	= new int[DEFAULT_STACK_SIZE];
		this.pcodes	= pcodes;
		this.input	= new Scanner(System.in);//默认输入为控制台输入
		this.output	= System.out;//默认输出为控制台输出
	}
	
	public void run() {
		int	pc		= 0;//程序计数器
		int	base	= 0;//基地址
		int	top		= 0;//栈顶
		do {
			Pcode pcode = pcodes.get(pc++);//获取当前需要执行的语句
			switch (pcode.getF()) {
				//为被调用的过程（包括主过程）在运行栈S中开辟数据区，
				//这时A段为所需数据单元个数（包括三个连接数据）；L段恒为0。
				case INT:
					top = top + pcode.getA();
					break;
				
				//调用过程，这时A段为被调用过程的过程体（过程体之前一条指令）在目标程序区的入口地址。
				case CAL:
					//跳转时，将该层基地址，跳转层基地址，pc指针保存在栈中
					//基地址base变为此时栈顶top，pc指向要跳转的地方
					//不修改top，因为前面代码已经将address+3，生成Pcode后会产生INT语句，修改top值
					stack[top] = base;
					stack[top + 1] = getBase(base, pcode.getL());
					stack[top + 2] = pc;
					base = top;
					pc = pcode.getA();
					break;
				
				//将常量送到运行栈S的栈顶，这时A段为常量值。
				case LIT:
					stack[top++] = pcode.getA();
					break;
				
				//将变量送到运行栈S的栈顶，这时A段为变量所在说明层中的相对位置。
				case LOD:
					stack[top++] = stack[pcode.getA() + getBase(base, pcode.getL())];
					break;
				
				//将运行栈S的栈顶内容送入某个变量单元中，A段为变量所在说明层中的相对位置。
				case STO:
					stack[pcode.getA() + getBase(base, pcode.getL())] = stack[--top];
					break;
				
				//无条件转移，这时A段为转向地址（目标程序）。
				case JMP:
					pc = pcode.getA();
					break;
				
				//条件转移，当运行栈S的栈顶的布尔值为假（0）时，则转向A段所指目标程序地址；否则顺序执行。
				case JPC:
					if (stack[top - 1] == 0) {
						pc = pcode.getA();
					}
					break;
				
				//关系或算术运算，A段指明具体运算
				case OPR:
					switch (pcode.getA()) {
						//OPR 0 0   过程调用结束后,返回调用点并退栈
						case 0:
							top = base;
							pc = stack[base + 2];
							base = stack[base];
							break;
						
						//OPR 0 1     栈顶元素取反
						case 1:
							stack[top - 1] = -stack[top - 1];
							break;
						
						//OPR 0 2   次栈顶与栈顶相加，退两个栈元素，结果值进栈
						case 2:
							stack[top - 2] = stack[top - 1] + stack[top - 2];
							--top;
							break;
						
						//OPR 0 3   次栈顶减去栈顶，退两个栈元素，结果值进栈
						case 3:
							stack[top - 2] = stack[top - 2] - stack[top - 1];
							--top;
							break;
						
						//OPR 0 4     次栈顶乘以栈顶，退两个栈元素，结果值进栈
						case 4:
							stack[top - 2] = stack[top - 1] * stack[top - 2];
							--top;
							break;
						
						//OPR 0 5     次栈顶除以栈顶，退两个栈元素，结果值进栈
						case 5:
							stack[top - 2] = stack[top - 2] / stack[top - 1];
							--top;
							break;
						
						//OPR 0 6   栈顶元素的奇偶判断，结果值在栈顶
						case 6:
							stack[top - 1] = stack[top - 1] % 2;
							break;
						case 7:
							
							break;
						
						//OPR 0 8   次栈顶与栈顶是否相等，退两个栈元素，结果值进栈
						case 8:
							if (stack[top - 2] == stack[top - 1]) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//OPR 0 9   次栈顶与栈顶是否不等，退两个栈元素，结果值进栈
						case 9:
							if (stack[top - 2] != stack[top - 1]) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//OPR 0 10  次栈顶是否小于栈顶，退两个栈元素，结果值进栈
						case 10:
							if (stack[top - 2] < stack[top - 1]) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//OPR 0 11    次栈顶是否大于等于栈顶，退两个栈元素，结果值进栈
						case 11:
							if (stack[top - 2] >= stack[top - 1]) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//OPR 0 12  次栈顶是否大于栈顶，退两个栈元素，结果值进栈
						case 12:
							if (stack[top - 2] > stack[top - 1]) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//OPR 0 13  次栈顶是否小于等于栈顶，退两个栈元素，结果值进栈
						case 13:
							if (stack[top - 2] <= stack[top - 1]) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//OPR 0 14  栈顶值输出至屏幕
						case 14:
							output.print(stack[top - 1]);
							output.print(" ");
							break;
						
						//OPR 0 15  屏幕输出换行
						case 15:
							output.println();
							break;
						
						//OPR 0 16  从命令行读入一个输入置于栈顶
						case 16:
							output.println("Need input:");
							stack[top] = input.nextInt();
							++top;
							break;
					}
					break;
				default:
					throw new RuntimeException();
			}
		} while (pc != 0);
	}
	
	//已知该层基地址为nowBp,得到层差为lev的层的基地址
	private int getBase(int nowBp, int lev) {
		int oldBp = nowBp;
		while (lev > 0) {
			oldBp = stack[oldBp + 1];
			lev--;
		}
		return oldBp;
	}
}
