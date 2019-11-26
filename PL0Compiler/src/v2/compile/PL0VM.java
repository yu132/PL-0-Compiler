package v2.compile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import v2.define.Pcode;

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
		this(DEFAULT_STACK_SIZE, pcodes);
	}
	
	public PL0VM(int stackSize, List<Pcode> pcodes) {
		super();
		this.stack	= new int[stackSize];
		this.pcodes	= pcodes;
		this.input	= new Scanner(System.in);//默认输入为控制台输入
		this.output	= System.out;//默认输出为控制台输出
	}
	
	public void run() {
		int	pc		= 0;//程序计数器
		int	base	= 0;//基地址（栈底）
		int	top		= 0;//栈顶
		do {
			Pcode pcode = pcodes.get(pc++);//获取当前需要执行的语句
			
			//System.out.println(pcode);
			
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
					
					/*
					 * 在这里做详细的调用的解释
					 * 
					 * 第一个位置（stack[top]）是上一次调用的过程的基址
					 * 第三个位置（stack[top + 2]）是上一次调用的过程的PC
					 * 
					 * 主要让人迷惑的是第二个位置（stack[top + 1]）存储的内容
					 * 这个位置实际上存储的是“声明这个过程”的过程的基地址（为什么后面再提）
					 * 
					 * 让我们举个例子吧 例如有1、2、3、4这4个过程
					 * 
					 * 1调用2，2调用3，3调用4
					 * 
					 * 那么在3调用4这次的CALL语句中
					 * 
					 * stack[top]指的是3的基地址
					 * stack[top + 2]指的是3当前的pc
					 * 
					 * 那么stack[top + 1]是什么呢？
					 * 
					 * 那就要看4是在何处声明的了
					 * 
					 * 我们假设1这个过程中声明了2、3、4共3个过程
					 * 
					 * 那么stack[top + 1]实际上是存储1的基地址
					 * 
					 * 一三两个位置的内容我们很明显知道是用来干什么的
					 * 因为上一次调用的基地址是用于过程结束后进行栈的还原的
					 * 存储PC也是同理，也需要还原PC的状态
					 * 
					 * ---那么存储“声明该过程的”的过程的基地址是为了什么呢？---
					 * 
					 * 我们首先要知道作用域这个概念，我们就能更清晰的了解这个问题
					 * 
					 * 如果一个过程中声明了一个子过程，那么子过程是可以调用父过程中定义的
					 * 的变量、常量和过程的，因此我们在这里定义“层级”的概念，子过程的上一级
					 * 是定义其的父过程，上两级是什么呢？那就是定义父过程的那个过程
					 * （前提是父过程不是主过程，如果是，那就不存在上两级）
					 * 
					 * 当一个子过程需要调用父过程（或者更高层级）定义的变量、常量
					 * 在自己的栈空间是找不到的，这个时候就需要通过父过程的基地址来找到我们需要的
					 * 而且定义在父过程的栈空间中的那些需要调用的变量、常量
					 * 
					 * 通过存储这种“上一级”的基地址，我们可以通过这种方法向上进行回溯
					 * 通过一次stack[base + 1]，可以找到父过程的基地址
					 * 那么stack[stack[base + 1]+1]就是爷爷过程的基地址，同理可以回退到主进程的base
					 * 
					 * 通过以上描述，我们知道了stack[top + 1]的作用
					 * 
					 * ---那么我们是如何获得stack[top + 1]这个值的呢？---
					 * 
					 * 首先我们要明白一个问题，一个过程，只能调用比自己层级高或同级的过程，
					 * 
					 * 不过先要明白一个点，那就是这个层级，是相对于当前申请调用的代码来说的
					 * 这个代码所属的过程中定义的过程和这些代码是同一级的，
					 * 如果这个代码调用了自己这个过程（递归），那么是高一级的
					 * 如果调用了父过程，那就是高2级的，然后可以依次向上推
					 * 
					 * 再举个例子吧 例如有1、2、3、4、5、6这4个过程
					 * 
					 * 1声明2，2声明3和4，3声明5，4声明6
					 * 
					 * 那么这个时候，5中的代码可以调用1(+4)、2(+3)、3(+2)、4(+2)、5(+1)这5个过程
					 * 6中的代码可以调用1(+4)、2(+3)、3(+3)、4(+2)、6(+1)这5个过程
					 * 3中的代码可以调用1(+3)、2(+2)、3(+1)、4(+1)、5(+0)这5个过程
					 * 4中的代码可以调用1(+3)、2(+2)、3(+1)、4(+1)、6(+0)这5个过程
					 * 2中的代码可以调用1(+2)、2(+1)、3(+0)、4(+0)这4个过程
					 * 1中的代码可以调用1(+1)、2(+0)这2个过程
					 * 
					 * 而看到getBase()中有一个参数pcode.getL()，这正是我们说的层差这个概念
					 * 
					 * 这个层差是我们在进行语法分析的时候，通过LEVEL来进行计算的
					 * 
					 * 例如当3中的代码调用5的时候，因为这个时候层差是0，所以我们调用 getBase()=base
					 * 因此此时stack[top + 1]就是3的基地址
					 * 
					 * 如果5调用1，那么层差是4，从5向上找，依次是3，2，1，最后是“声明1的过程”
					 * 因此此时stack[top + 1]是“声明1的过程”的基地址
					 * 
					 * 此时我们证明了stack[top + 1]这个位置存储的是“声明这个过程”的过程的基地址
					 * 
					 * ---我们是如何计算层差的呢？---
					 * 
					 * 首先我们要回到代码生成的内容，我们在代码生成的时候（GrammarAnalyzer中查找CAL）
					 * CAl这条语句的L=level - pro.getLevel()
					 * 而level是当前过程（调用某个过程的过程）的层级例如3调用5，那么这里是3的层级，而不是5的
					 * pro.getLevel()指的是“定义这个过程”的过程的层级，如3声明5，那么这里是3的层级
					 * 而此时的层差即为0
					 * 
					 * 若为5调用2，那么level为5的level，pro.getLevel()为声明2的过程的level，即1的level
					 * 两者中间差了3层，因为1声明2，2声明3，3声明5，确实是3层
					 * 
					 * 因此层差的计算也说明白了
					 * 
					 * ---最后说说栈的结构吧---
					 * 
					 * 一个最基础的过程单元（除了主过程）是由以下部分构成的
					 * 
					 * stack[base] stack[base + 1] stack[base + 2] stack[base + 3] ...  stack[top - 1]
					 *    (base) (base of high level)    (pc)           (var1   var2 ...   varn)
					 * 
					 * 那么由过程单元组成的栈总是以如下结构排布的
					 * 举刚刚6个过程的那个例子，1调用2，2调用3，3调用5，5调用2，2调用4，4调用6
					 * 
					 * 那么结构应该就是1 2 3 5 2 4 6，其中出现了两个2
					 * 这两个2中的stack[base + 1]都是1的base
					 * 而其中的3的stack[base + 1]是第一个2的base
					 * 4的stack[base + 1]是第二个2的base
					 * 
					 * 这里就形成了两个分支，1-2(第一个2)-3-5和1-2(第二个2)-4-6
					 * 
					 * 这两个2之间是不共享内存区域的
					 * 
					 * ---最后再补充一个问题，我们这种方法是不是总是可行，会不会向上找找不到呢？---
					 * 
					 * 答案是明显是可行的，因为上个栈结构告诉我们，当我们调用到某个过程的时候，声明这个过程
					 * 一定被调用过，而再想上找也能找到声明“声明这个过程”的过程，就是会产生一条链，这条链上
					 * 唯一的有按声明顺序产生的链，如上面1-2(第一个2)-3-5，就是按照1声明2，2声明3，3声明5的顺序来的
					 * 而恰好的是，5只能调用1(+4)、2(+3)、3(+2)、4(+2)、5(+1)，而声明这些过程的过程为：
					 * “声明1的过程”（这个在上面描述的链之前，也是存在的）、1、2、3，恰好就在这条链上（因此总是能够找到的）
					 */
					
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
				
				//条件转移，当运行栈S的栈顶的布尔值为假（0）时，则转向A段所指目标程序地址；否则顺序执行，并退栈顶元素。
				case JPC:
					if (stack[--top] == 0) {
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
							if (stack[top - 1] == 0) {
								System.err.println("Division by 0!");
								return;
							}
							stack[top - 2] = stack[top - 2] / stack[top - 1];
							--top;
							break;
						
						//OPR 0 6   栈顶元素的奇偶判断，退栈顶元素，结果值存回栈顶
						case 6:
							stack[top - 1] = stack[top - 1] % 2;
							break;
						case 7:
							stack[top - 2] = stack[top - 2] % stack[top - 1];
							--top;
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
						
						//OPR 0 14  栈顶值输出至屏幕，并退栈顶
						case 14:
							output.print(stack[--top]);
							output.print(" ");
							break;
						
						//OPR 0 15  屏幕输出换行
						case 15:
							output.println();
							break;
						
						//OPR 0 16  从命令行读入一个输入置于栈顶
						case 16:
							output.println("Need input:");
							stack[top++] = input.nextInt();
							break;
						
						//将栈顶元素进行逻辑非运算，即非0变成0，0变成1
						case 17:
							if (stack[top - 1] == 0) {
								stack[top - 1] = 1;
							} else {
								stack[top - 1] = 0;
							}
							break;
						
						//将栈顶和次栈进行逻辑与操作，退两个栈元素，当两者都是1的时候，栈顶进入1，否则进入0
						case 18:
							if (stack[top - 1] == 1 && stack[top - 2] == 1) {
								stack[top - 2] = 1;
							} else {
								stack[top - 2] = 0;
							}
							--top;
							break;
						
						//将栈顶和次栈进行逻辑或操作，退两个栈元素，当两者都是0的时候，栈顶进入0，否则进入1
						case 19:
							if (stack[top - 1] == 0 && stack[top - 2] == 0) {
								stack[top - 2] = 0;
							} else {
								stack[top - 2] = 1;
							}
							break;
					}
					break;
				
				//检查当前栈顶的值是否在0-A(不包括)这个范围之间，如果不是，引起数组越界错误
				case ARC: {
					if (stack[top - 1] < 0 || stack[top - 1] >= pcode.getA()) {
						System.err.println("List out of bound! range is 0-" + pcode.getA()
								+ " ,but index is " + stack[top - 1]);
						return;
					}
					break;
				}
				
				//将栈顶退栈，将该数设为T，将栈中A+T位置的变量至于栈顶
				case ARL: {
					stack[top - 1] = stack[pcode.getA() + stack[top - 1]
							+ getBase(base, pcode.getL())];
					break;
				}
				
				//将栈顶退栈，将该数设为T，将栈顶（运行前的次栈）放入栈中A+T位置
				case ARS: {
					int bias = stack[--top];
					stack[pcode.getA() + bias + getBase(base, pcode.getL())] = stack[--top];
					break;
				}
				
				//交换栈顶和次栈的值
				case SWAP: {
					int temp = stack[top - 1];
					stack[top - 1]	= stack[top - 2];
					stack[top - 2]	= temp;
					break;
				}
				case EXIT: {
					int exitCode = stack[top - 1];
					System.err.println("program exit, exit code:" + exitCode);
					return;
				}
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
