/**
 * The quick messages one circle member can send another. A fixed list: nothing the sender types reaches the
 * recipient, so it cannot be used for abuse, and every message has an emoji the recipient's phone rains down.
 */
export interface QuickMessageDef {
  code: string;
  text: string;
  emoji: string;
  /** Funny extras are collectibles: only people who earned this one can send it. */
  collectibleCode?: string;
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

/** Earned from levels and achievements (see the collection). */
export const COLLECTIBLE_QUICK_MESSAGES: QuickMessageDef[] = [
  { code: 'NOTED_CAPTAIN', text: 'Noted, captain!', emoji: '🎖️', collectibleCode: 'qm_noted' },
  { code: 'SNACK_TIME', text: 'Snack time?', emoji: '🍜', collectibleCode: 'qm_snack' },
  { code: 'BEEP_BEEP', text: 'Beep beep!', emoji: '🚙', collectibleCode: 'qm_beep' },
  { code: 'TOO_SLOW', text: 'Too slow!', emoji: '🐌', collectibleCode: 'qm_slow' },
  { code: 'GOAT', text: "You're the GOAT", emoji: '🐐', collectibleCode: 'qm_goat' },
  { code: 'SPILL_TEA', text: 'Spill the tea', emoji: '☕', collectibleCode: 'qm_tea' },
  { code: 'LETS_PARTY', text: "Let's party!", emoji: '🎉', collectibleCode: 'qm_party' },
  { code: 'BOO', text: 'Boo!', emoji: '👻', collectibleCode: 'qm_ghost' },
];

export const quickMessage = (code: string) => [...QUICK_MESSAGES, ...COLLECTIBLE_QUICK_MESSAGES].find((m) => m.code === code);
