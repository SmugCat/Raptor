package raptor.connector.fics.chat;

import raptor.chat.ChatEvent;

public class PartnershipEndedEventParser extends ChatEventParser {
	public PartnershipEndedEventParser() {
	}

	public ChatEvent parse(String text) {
		if (text.length() < 100) {
			if (text.indexOf(ID_1) != -1)
				return new ChatEvent(null, PARTNERSHIP_DESTROYED, text);
			else if (text.indexOf(ID_2) != -1)
				return new ChatEvent(null, PARTNERSHIP_DESTROYED, text);
			else
				return null;
		}
		return null;
	}

	private static final String ID_1 = "You no longer have a bughouse partner.";

	private static final String ID_2 = "Your partner has ended partnership.";
}