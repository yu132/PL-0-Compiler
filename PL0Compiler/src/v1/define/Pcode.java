package v1.define;

/**
 *  中间代码类
 *  
 *  代码的格式为
 *   F  L  A
 *   
 *   其中：
 *   F段代表伪操作码
 *   L段代表调用层与说明层的层差值
 *   A段代表位移量（相对地址）
 *         
 * @author 87663
 *
 */
public class Pcode {
	
	private Operator	f;
	private int			l;
	private int			a;
	
	public static Pcode code(Operator f, int l, int a) {
		return new Pcode(f, l, a);
	}
	
	public Pcode(Operator f, int l, int a) {
		super();
		this.f	= f;
		this.l	= l;
		this.a	= a;
	}
	
	public Operator getF() {
		return f;
	}
	
	public int getL() {
		return l;
	}
	
	public int getA() {
		return a;
	}
	
	public void setF(Operator f) {
		this.f = f;
	}
	
	public void setL(int l) {
		this.l = l;
	}
	
	public void setA(int a) {
		this.a = a;
	}
	
	
}
