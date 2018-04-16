package fr.eisbm.SBGN_AF_CONVERTER;

public class PortInformation {
	String m_id;
	float m_X;
	float m_Y;
	
	public PortInformation() {
		super();
	}
	
	public PortInformation(String m_id, float m_X, float m_Y) {
		super();
		this.m_id = m_id;
		this.m_X = m_X;
		this.m_Y = m_Y;
	}

	public String getId() {
		return m_id;
	}

	public void setId(String m_id) {
		this.m_id = m_id;
	}

	public float getX() {
		return m_X;
	}

	public void setX(float X) {
		this.m_X = X;
	}

	public float getY() {
		return m_Y;
	}

	public void setY(float Y) {
		this.m_Y = Y;
	}

	@Override
	public String toString() {
		return "PortInformation [m_id=" + m_id + ", m_X=" + m_X + ", m_Y=" + m_Y + "]";
	}

}
