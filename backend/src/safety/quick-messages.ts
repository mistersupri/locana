/**
 * The quick messages one circle member can send another. A fixed list: nothing the sender types reaches the
 * recipient, so it cannot be used for abuse, and every message has an emoji the recipient's phone rains down.
 */
export interface QuickMessageDef {
  code: string;
  text: string;
  emoji: string;
}

export const QUICK_MESSAGES: QuickMessageDef[] = [
  { code: 'LOVE', text: 'Love ya!', emoji: '❤️' },
  { code: 'SLOW_DOWN', text: 'Slow down', emoji: '😳' },
  { code: 'CALL_ME', text: 'Call me', emoji: '📞' },
  { code: 'WHERE_ARE_YOU', text: 'Where are you?', emoji: '📍' },
  { code: 'ON_MY_WAY', text: 'On my way', emoji: '🚗' },
  { code: 'BE_CAREFUL', text: 'Be careful', emoji: '🙏' },
  { code: 'THANK_YOU', text: 'Thank you', emoji: '🙌' },
  { code: 'HAHA', text: 'Haha', emoji: '😂' },
];

export const quickMessage = (code: string) => QUICK_MESSAGES.find((m) => m.code === code);
