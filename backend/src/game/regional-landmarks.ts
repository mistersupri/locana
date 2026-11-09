/**
 * Public places for the cities around Jakarta. Each region is a collectible set: visit every place to
 * earn its badge. Centre points are written from general knowledge of each place and are approximate
 * (radii are generous to cover that); correct any of them with PATCH /v1/admin/landmarks, or add more
 * with POST /v1/admin/landmarks. Only public places, never homes.
 */
export interface RegionalLandmark {
  code: string;
  name: string;
  category: string;
  latitude: number;
  longitude: number;
  radiusM: number;
  description: string;
}

export interface Region {
  setCode: string;
  title: string;
  badgeCode: string;
  badgeName: string;
  landmarks: RegionalLandmark[];
}

export const REGIONS: Region[] = [
  {
    setCode: 'jakarta_more',
    title: 'Jakarta classics',
    badgeCode: 'badge_jakarta_classics',
    badgeName: 'Jakarta Classics badge',
    landmarks: [
      { code: 'grand_indonesia', name: 'Grand Indonesia', category: 'shopping', latitude: -6.1951, longitude: 106.8197, radiusM: 150, description: 'Large mall next to Bundaran HI.' },
      { code: 'plaza_indonesia', name: 'Plaza Indonesia', category: 'shopping', latitude: -6.1938, longitude: 106.8219, radiusM: 120, description: 'Mall on Jalan Thamrin.' },
      { code: 'plaza_senayan', name: 'Plaza Senayan', category: 'shopping', latitude: -6.2254, longitude: 106.7995, radiusM: 150, description: 'Mall beside Senayan.' },
      { code: 'senayan_city', name: 'Senayan City', category: 'shopping', latitude: -6.2272, longitude: 106.797, radiusM: 150, description: 'Mall across from Plaza Senayan.' },
      { code: 'kota_kasablanka', name: 'Kota Kasablanka', category: 'shopping', latitude: -6.2241, longitude: 106.8423, radiusM: 180, description: 'Mall and tower complex in Tebet.' },
      { code: 'pondok_indah_mall', name: 'Pondok Indah Mall', category: 'shopping', latitude: -6.2655, longitude: 106.7829, radiusM: 200, description: 'Mall in South Jakarta.' },
      { code: 'stasiun_jakarta_kota', name: 'Stasiun Jakarta Kota', category: 'station', latitude: -6.1376, longitude: 106.8143, radiusM: 120, description: 'Historic terminus station in Kota Tua.' },
      { code: 'stasiun_manggarai', name: 'Stasiun Manggarai', category: 'station', latitude: -6.2099, longitude: 106.8504, radiusM: 150, description: 'Busy interchange station.' },
      { code: 'stasiun_gondangdia', name: 'Stasiun Gondangdia', category: 'station', latitude: -6.1861, longitude: 106.8326, radiusM: 100, description: 'Colonial-era station in Menteng.' },
      { code: 'stasiun_pasar_senen', name: 'Stasiun Pasar Senen', category: 'station', latitude: -6.1747, longitude: 106.8442, radiusM: 120, description: 'Long-distance station in Central Jakarta.' },
      { code: 'museum_wayang', name: 'Museum Wayang', category: 'museum', latitude: -6.1349, longitude: 106.813, radiusM: 80, description: 'Puppet museum on Taman Fatahillah.' },
      { code: 'museum_bahari', name: 'Museum Bahari', category: 'museum', latitude: -6.1272, longitude: 106.8062, radiusM: 120, description: 'Maritime museum in old VOC warehouses.' },
      { code: 'cut_meutia', name: 'Masjid Cut Meutia', category: 'religious', latitude: -6.1851, longitude: 106.8355, radiusM: 80, description: 'Former colonial office turned mosque in Menteng.' },
      { code: 'patung_tani', name: 'Tugu Tani', category: 'monument', latitude: -6.1778, longitude: 106.8346, radiusM: 100, description: 'Farmer statue at the Menteng junction.' },
      { code: 'pasar_baru', name: 'Pasar Baru', category: 'market', latitude: -6.1655, longitude: 106.8363, radiusM: 150, description: 'One of the oldest shopping streets in Jakarta.' },
      { code: 'glodok', name: 'Glodok', category: 'market', latitude: -6.1445, longitude: 106.8165, radiusM: 200, description: 'Chinatown market district.' },
      { code: 'jin_de_yuan', name: 'Vihara Dharma Bhakti (Jin De Yuan)', category: 'religious', latitude: -6.1431, longitude: 106.8152, radiusM: 100, description: 'Historic Chinese temple in Glodok.' },
      { code: 'setu_babakan', name: 'Setu Babakan', category: 'park', latitude: -6.3419, longitude: 106.8294, radiusM: 300, description: 'Lake and Betawi cultural village in South Jakarta.' },
    ],
  },
  {
    setCode: 'bekasi',
    title: 'Bekasi',
    badgeCode: 'badge_bekasi',
    badgeName: 'Bekasi Explorer badge',
    landmarks: [
      { code: 'bks_summarecon', name: 'Summarecon Mall Bekasi', category: 'shopping', latitude: -6.2262, longitude: 106.9996, radiusM: 200, description: 'Large mall in North Bekasi.' },
      { code: 'bks_metropolitan', name: 'Grand Metropolitan Mall', category: 'shopping', latitude: -6.2467, longitude: 106.9918, radiusM: 200, description: 'Mall on Jalan KH Noer Ali, Bekasi.' },
      { code: 'bks_mega_mall', name: 'Mega Bekasi Hypermall', category: 'shopping', latitude: -6.2486, longitude: 106.9918, radiusM: 200, description: 'Mall on Jalan Ahmad Yani, Bekasi.' },
      { code: 'bks_stasiun', name: 'Stasiun Bekasi', category: 'station', latitude: -6.2356, longitude: 106.9995, radiusM: 150, description: 'Main train station of Bekasi.' },
      { code: 'bks_alun_alun', name: 'Alun-Alun Kota Bekasi', category: 'square', latitude: -6.2381, longitude: 106.9926, radiusM: 200, description: 'Central town square of Bekasi.' },
      { code: 'bks_patriot', name: 'Stadion Patriot Candrabhaga', category: 'stadium', latitude: -6.218, longitude: 106.9976, radiusM: 300, description: 'Football stadium of Bekasi.' },
      { code: 'bks_juanda', name: 'Bekasi Cyber Park', category: 'park', latitude: -6.2453, longitude: 106.9937, radiusM: 250, description: 'Public park and event ground in Bekasi.' },
      { code: 'bks_harapan_indah', name: 'Boulevard Hijau Harapan Indah', category: 'park', latitude: -6.1857, longitude: 106.9712, radiusM: 400, description: 'Lakeside leisure area in Harapan Indah.' },
      { code: 'bks_kalimalang', name: 'Kalimalang Canal Walk', category: 'park', latitude: -6.2472, longitude: 106.9858, radiusM: 400, description: 'Waterside stroll along the Kalimalang canal.' },
      { code: 'bks_summarecon_park', name: 'Summarecon Bekasi Central Park', category: 'park', latitude: -6.2259, longitude: 107.0017, radiusM: 300, description: 'Green space beside Summarecon Mall.' },
    ],
  },
  {
    setCode: 'depok',
    title: 'Depok',
    badgeCode: 'badge_depok',
    badgeName: 'Depok Explorer badge',
    landmarks: [
      { code: 'dpk_ui_library', name: 'Perpustakaan Pusat UI', category: 'library', latitude: -6.3648, longitude: 106.8294, radiusM: 200, description: 'The Crystal of Knowledge, the university library.' },
      { code: 'dpk_ui_station', name: 'Stasiun UI', category: 'station', latitude: -6.3607, longitude: 106.8317, radiusM: 150, description: 'Train station beside the University of Indonesia.' },
      { code: 'dpk_ui_lake', name: 'Danau Kenanga UI', category: 'park', latitude: -6.3665, longitude: 106.8258, radiusM: 250, description: 'Lake on the campus, open to visitors.' },
      { code: 'dpk_margo_city', name: 'Margo City', category: 'shopping', latitude: -6.3735, longitude: 106.8332, radiusM: 200, description: 'Mall on Jalan Margonda Raya.' },
      { code: 'dpk_detos', name: 'Depok Town Square', category: 'shopping', latitude: -6.371, longitude: 106.8334, radiusM: 180, description: 'Mall on Jalan Margonda Raya.' },
      { code: 'dpk_stasiun_baru', name: 'Stasiun Depok Baru', category: 'station', latitude: -6.3912, longitude: 106.8217, radiusM: 150, description: 'Commuter station in the city centre.' },
      { code: 'dpk_stasiun_lama', name: 'Stasiun Depok Lama', category: 'station', latitude: -6.4053, longitude: 106.8183, radiusM: 150, description: 'Old Depok station near the colonial quarter.' },
      { code: 'dpk_kubah_emas', name: 'Masjid Kubah Emas (Dian Al-Mahri)', category: 'religious', latitude: -6.3953, longitude: 106.7741, radiusM: 300, description: 'The golden-domed mosque in Limo.' },
      { code: 'dpk_cinere_mall', name: 'Cinere Mall', category: 'shopping', latitude: -6.3236, longitude: 106.7845, radiusM: 200, description: 'Mall at the western edge of Depok.' },
      { code: 'dpk_pesona', name: 'Pesona Square', category: 'shopping', latitude: -6.3899, longitude: 106.8268, radiusM: 180, description: 'Mall on Jalan Margonda Raya.' },
    ],
  },
];
