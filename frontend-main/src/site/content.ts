// Editorial content for the public website. Copy describes the school's
// planned approach — it is a new school, so nothing here claims history,
// results, numbers or testimonials.
//
// Photos: every entry in PHOTOS renders an illustrated placeholder until a
// `src` is set. To use a real photograph, add it under public/site/photos/
// and set `src: '/site/photos/<file>.jpg'`. Only use photos the school has
// permission to publish.

import type { LucideIcon } from 'lucide-react';
import {
  BookOpen,
  Blocks,
  Brush,
  CalendarCheck,
  Compass,
  Droplets,
  Eye,
  FlaskConical,
  Footprints,
  HandHeart,
  Handshake,
  Heart,
  Hourglass,
  Lightbulb,
  MessageCircle,
  MessagesSquare,
  MonitorSmartphone,
  Moon,
  Music,
  Palette,
  Puzzle,
  Ruler,
  Search,
  Shapes,
  ShieldCheck,
  Smile,
  Sparkles,
  Sprout,
  Sun,
  Telescope,
  TreePine,
  Users,
  Utensils,
} from 'lucide-react';

export type PhotoSpec = {
  /** Real image path; leave undefined to show the illustrated placeholder. */
  src?: string;
  alt: string;
  /** Short description of the photo that should go here. */
  hint: string;
  icon: LucideIcon;
};

const PHOTO_SPECS = {
  hero: {
    alt: 'Young children exploring natural objects together at a low table',
    hint: 'Children investigating leaves, shells and stones',
    icon: Search,
  },
  wonder: {
    alt: 'A child looking closely at a leaf through a magnifying glass',
    hint: 'A child studying a leaf with a magnifier',
    icon: Telescope,
  },
  explore: {
    alt: 'Children exploring a sensory tray of natural materials',
    hint: 'Hands in a tray of sand, seeds and pebbles',
    icon: Compass,
  },
  create: {
    alt: 'Children building a tall structure with wooden blocks',
    hint: 'Building a tower with wooden blocks',
    icon: Blocks,
  },
  reflect: {
    alt: 'A small group of children sharing ideas with their teacher on a rug',
    hint: 'Children talking about what they noticed',
    icon: MessagesSquare,
  },
  thinking: {
    alt: 'Children testing which objects float in a water tray',
    hint: 'Testing what floats in a water tray',
    icon: Droplets,
  },
  garden: {
    alt: 'Children watering seedlings in a school garden bed',
    hint: 'Watering seedlings in the garden',
    icon: Sprout,
  },
  science: {
    alt: 'Children mixing colours in clear cups during a simple experiment',
    hint: 'A colour-mixing experiment',
    icon: FlaskConical,
  },
  story: {
    alt: 'A teacher reading a picture book to a small group of children',
    hint: 'Story time with a picture book',
    icon: BookOpen,
  },
  art: {
    alt: 'Children painting and constructing with recycled materials',
    hint: 'Painting and junk-modelling',
    icon: Palette,
  },
  play: {
    alt: 'Children working together to complete a large floor puzzle',
    hint: 'Solving a floor puzzle together',
    icon: Puzzle,
  },
  partnership: {
    alt: 'A parent and teacher looking at a child’s learning journal together',
    hint: 'A parent and teacher sharing a learning journal',
    icon: Handshake,
  },
  admissions: {
    alt: 'A welcoming, sunlit early-years classroom with low shelves and plants',
    hint: 'A calm, sunlit classroom',
    icon: Sun,
  },
  classroom: {
    alt: 'An early-years classroom arranged into learning zones',
    hint: 'Classroom learning zones',
    icon: Shapes,
  },
} satisfies Record<string, PhotoSpec>;

export type PhotoKey = keyof typeof PHOTO_SPECS;
export const PHOTOS: Record<PhotoKey, PhotoSpec> = PHOTO_SPECS;

// ---------------------------------------------------------------------------

export const SEQUENCE = [
  { label: 'Wonder', icon: Sparkles },
  { label: 'Explore', icon: Compass },
  { label: 'Discover', icon: Lightbulb },
  { label: 'Create', icon: Brush },
  { label: 'Grow', icon: Sprout },
] as const;

export type Pillar = {
  id: string;
  title: string;
  body: string;
  icon: LucideIcon;
  link: { to: '/our-philosophy' | '/learning-approach' | '/programmes' | '/a-day-at-school'; label: string };
};

export const PILLARS: Pillar[] = [
  {
    id: 'thinking',
    title: 'Thinking & Discovery',
    body: 'Children learn to observe, ask questions, explore possibilities, and discover how things work.',
    icon: Lightbulb,
    link: { to: '/learning-approach', label: 'How inquiry works' },
  },
  {
    id: 'faith',
    title: 'Faith & Character',
    body: 'Islamic values come to life through kindness, honesty, gratitude, responsibility, and meaningful stories.',
    icon: Moon,
    link: { to: '/our-philosophy', label: 'Faith in everyday life' },
  },
  {
    id: 'creativity',
    title: 'Creativity & Expression',
    body: 'Children turn ideas into drawings, constructions, stories, and imaginative creations.',
    icon: Palette,
    link: { to: '/learning-approach', label: 'Creative learning' },
  },
  {
    id: 'experiential',
    title: 'Experiential Learning',
    body: 'Real materials, real questions and real places — from the garden to the water tray.',
    icon: FlaskConical,
    link: { to: '/a-day-at-school', label: 'See a school day' },
  },
  {
    id: 'social',
    title: 'Emotional & Social Growth',
    body: 'Naming feelings, taking turns, solving problems together and building friendships.',
    icon: Heart,
    link: { to: '/programmes', label: 'Development by stage' },
  },
  {
    id: 'life',
    title: 'Life Skills & Digital Awareness',
    body: 'Independence, self-care and a careful, age-appropriate first look at technology.',
    icon: MonitorSmartphone,
    link: { to: '/programmes', label: 'Our programmes' },
  },
];

export const APPROACH = [
  {
    step: '01',
    title: 'Explore',
    body: 'Children investigate materials, nature, stories, and real-world questions.',
    photo: 'explore' as PhotoKey,
  },
  {
    step: '02',
    title: 'Create',
    body: 'Children build, draw, experiment, imagine, and express ideas.',
    photo: 'create' as PhotoKey,
  },
  {
    step: '03',
    title: 'Reflect',
    body: 'Children explain what they noticed, consider different possibilities, and learn from experience.',
    photo: 'reflect' as PhotoKey,
  },
];

export const VALUES = [
  {
    arabic: 'شُكْر',
    translit: 'Shukr',
    english: 'Gratitude',
    icon: Sun,
    example:
      'Noticing everyday blessings — rain for the garden, food at snack time, a friend’s help — and learning to say Alhamdulillah with meaning.',
  },
  {
    arabic: 'صَبْر',
    translit: 'Sabr',
    english: 'Patience',
    icon: Hourglass,
    example:
      'Waiting for seeds to sprout, taking turns with a favourite toy, and trying again when a tower falls down.',
  },
  {
    arabic: 'رَحْمَة',
    translit: 'Rahmah',
    english: 'Kindness & mercy',
    icon: HandHeart,
    example:
      'Comforting a classmate who is upset, caring gently for living things, and choosing kind words.',
  },
  {
    arabic: 'أَمَانَة',
    translit: 'Amanah',
    english: 'Responsibility',
    icon: ShieldCheck,
    example:
      'Looking after shared materials, tidying learning spaces, and taking turns to water the class plants.',
  },
];

export const GALLERY: { photo: PhotoKey; title: string; caption: string }[] = [
  { photo: 'create', title: 'Building structures', caption: 'Balancing, testing and rebuilding — early engineering in action.' },
  { photo: 'garden', title: 'Gardening & nature', caption: 'Planting, watering and watching patiently as things grow.' },
  { photo: 'science', title: 'Practical science', caption: 'Simple experiments that start with “What will happen if…?”' },
  { photo: 'story', title: 'Stories & language', caption: 'Rich stories that grow vocabulary, imagination and empathy.' },
  { photo: 'art', title: 'Art & construction', caption: 'Ideas made visible with paint, clay and recycled materials.' },
  { photo: 'play', title: 'Cooperative play', caption: 'Sharing a goal, negotiating roles and solving it together.' },
];

export const DAY_GLIMPSE = [
  { title: 'Arrive & Connect', body: 'A warm welcome, greetings and settling in with friends.', icon: Smile },
  { title: 'Wonder & Explore', body: 'A question, object or story sparks the day’s curiosity.', icon: Sparkles },
  { title: 'Learn & Discover', body: 'Hands-on activities in language, number and the world around us.', icon: Lightbulb },
  { title: 'Move & Play', body: 'Outdoor movement, games and fresh air.', icon: Footprints },
  { title: 'Create & Collaborate', body: 'Building, making and working on ideas together.', icon: Users },
  { title: 'Reflect & Share', body: 'Talking about what we noticed, learned and are thankful for.', icon: MessageCircle },
];

export const DAY_DETAIL: {
  title: string;
  body: string;
  younger: string;
  older: string;
  icon: LucideIcon;
  photo?: PhotoKey;
}[] = [
  {
    title: 'Arrival and settling in',
    body: 'Children are greeted by name, hang up their belongings and choose a calm activity while friends arrive.',
    younger: 'Extra time with a familiar adult; a simple, predictable routine helps them feel secure.',
    older: 'Children self-register and take on small responsibilities such as checking the plants.',
    icon: Smile,
    photo: 'admissions',
  },
  {
    title: 'Morning circle',
    body: 'The group gathers to greet each other, talk about the day and share something they are curious about.',
    younger: 'Short, song-rich circles with lots of movement.',
    older: 'Children lead parts of the circle and help plan the day.',
    icon: Users,
  },
  {
    title: 'Language and literacy',
    body: 'Stories, rhymes, sounds and mark-making build a love of words and early reading and writing.',
    younger: 'Listening games, picture books and conversation.',
    older: 'Letter sounds, early writing and retelling stories in their own words.',
    icon: BookOpen,
    photo: 'story',
  },
  {
    title: 'Hands-on discovery',
    body: 'Investigations with real materials — water, plants, light, shapes and building — guided by children’s questions.',
    younger: 'Sensory exploration: pouring, filling, sorting and feeling.',
    older: 'Predicting, testing and recording what they find.',
    icon: FlaskConical,
    photo: 'science',
  },
  {
    title: 'Snack and personal care',
    body: 'A calm, social break where children practise independence, good manners and saying Bismillah and Alhamdulillah.',
    younger: 'Adults support handwashing, opening containers and tidying up.',
    older: 'Children serve themselves, help set up and clean up.',
    icon: Utensils,
  },
  {
    title: 'Outdoor movement',
    body: 'Running, climbing, balancing and exploring the outdoors build strong bodies and confident minds.',
    younger: 'Free movement, ball play and gentle obstacle paths.',
    older: 'Team games, garden jobs and nature hunts.',
    icon: TreePine,
    photo: 'garden',
  },
  {
    title: 'Creative learning centres',
    body: 'Children choose between art, construction, role play, puzzles and music to follow their ideas.',
    younger: 'Open-ended materials with lots of adult conversation.',
    older: 'Longer projects that continue over several days.',
    icon: Palette,
    photo: 'art',
  },
  {
    title: 'Islamic stories and reflection',
    body: 'Age-appropriate stories, short duas and gentle discussion connect Islamic values to children’s everyday lives.',
    younger: 'Short stories with pictures and simple, repeated duas.',
    older: 'Talking about how characters showed kindness, patience or honesty.',
    icon: Moon,
  },
  {
    title: 'Sharing and departure',
    body: 'Children share something they made or noticed, tidy up together and get ready to go home.',
    younger: 'A goodbye song and a calm handover to families.',
    older: 'Reflecting on one thing they learned and one thing they are thankful for.',
    icon: MessageCircle,
  },
];

export const PROGRAMMES: {
  id: string;
  name: string;
  ageBand: string;
  stage: string;
  objectives: string[];
  activities: string[];
  areas: { label: string; body: string }[];
}[] = [
  {
    id: 'early-years',
    name: 'Early Years',
    ageBand: 'around 2–3 years',
    stage: 'Discovering the world through the senses, movement and close, trusting relationships with caring adults.',
    objectives: ['Feel safe, settled and happy away from home', 'Develop early language and communication', 'Build independence in simple routines'],
    activities: ['Sensory trays and water play', 'Songs, rhymes and finger play', 'Simple stacking, posting and sorting'],
    areas: [
      { label: 'Language & literacy', body: 'Rich conversation, picture books, naming objects and joining in with rhymes.' },
      { label: 'Early mathematics', body: 'Noticing big and small, filling and emptying, and counting in songs.' },
      { label: 'Islamic learning', body: 'Hearing Bismillah and Alhamdulillah in daily routines and short, gentle stories.' },
      { label: 'Social-emotional', body: 'Forming secure attachments, playing alongside others and beginning to share.' },
      { label: 'Creative & physical', body: 'Mark-making, music, crawling, climbing and lots of free movement.' },
    ],
  },
  {
    id: 'nursery',
    name: 'Nursery',
    ageBand: 'around 3–4 years',
    stage: 'Asking endless questions, playing imaginatively and beginning to play cooperatively with friends.',
    objectives: ['Ask questions and explore ideas', 'Develop confident speaking and listening', 'Begin to recognise sounds, shapes and numbers'],
    activities: ['Float-or-sink and planting investigations', 'Role play and small-world play', 'Block building and simple patterns'],
    areas: [
      { label: 'Language & literacy', body: 'Storytelling, listening games, early sounds and enjoying books together.' },
      { label: 'Early mathematics', body: 'Counting real objects, comparing sizes, sorting and spotting patterns.' },
      { label: 'Islamic learning', body: 'Short duas, stories of kindness and gratitude, and learning about Allah’s creation in nature.' },
      { label: 'Social-emotional', body: 'Taking turns, naming feelings and solving small problems with friends.' },
      { label: 'Creative & physical', body: 'Painting, modelling, music, balancing and developing pencil grip through play.' },
    ],
  },
  {
    id: 'kindergarten',
    name: 'Kindergarten',
    ageBand: 'around 4–5 years',
    stage: 'Growing confidence, longer attention and a strong desire to make, test and explain.',
    objectives: ['Predict, test and explain what they notice', 'Begin reading and writing simple words', 'Work together on shared projects'],
    activities: ['Multi-day building and design projects', 'Garden and nature journals', 'Measuring, comparing and simple data'],
    areas: [
      { label: 'Language & literacy', body: 'Phonics through play, early writing, retelling stories and presenting ideas.' },
      { label: 'Early mathematics', body: 'Number sense, adding and taking away with objects, shapes, measuring and time.' },
      { label: 'Islamic learning', body: 'Stories of the Prophets, everyday duas and reflecting on values such as honesty and patience.' },
      { label: 'Social-emotional', body: 'Collaborating, resolving disagreements and showing care for others.' },
      { label: 'Creative & physical', body: 'Design-and-build, drama, music, team games and fine-motor skills for writing.' },
    ],
  },
  {
    id: 'school-readiness',
    name: 'School Readiness',
    ageBand: 'around 5–6 years',
    stage: 'Building independence, resilience and the learning habits that support a confident move into formal schooling.',
    objectives: ['Read and write with growing independence', 'Reason and explain mathematical thinking', 'Manage belongings, time and feelings'],
    activities: ['Investigation projects with simple recording', 'Book-making and story-writing', 'Responsibility roles in the classroom'],
    areas: [
      { label: 'Language & literacy', body: 'Reading simple books, writing sentences and speaking confidently to a group.' },
      { label: 'Early mathematics', body: 'Number bonds, problem-solving, money, measuring and explaining strategies.' },
      { label: 'Islamic learning', body: 'Deepening understanding of Islamic values, manners and stories, and how they guide our choices.' },
      { label: 'Social-emotional', body: 'Resilience, self-regulation and taking responsibility for actions.' },
      { label: 'Creative & physical', body: 'Extended creative projects, coordination games and confident handwriting.' },
    ],
  },
];

export const LEARNING_ZONES = [
  { title: 'Discovery & science', body: 'Magnifiers, water and light tables, natural objects and simple tools.', icon: FlaskConical },
  { title: 'Construction', body: 'Blocks, loose parts and design challenges for early engineers.', icon: Blocks },
  { title: 'Reading corner', body: 'A cosy space with picture books, storytelling props and puppets.', icon: BookOpen },
  { title: 'Art studio', body: 'Paint, clay, collage and recycled materials always within reach.', icon: Palette },
  { title: 'Role play', body: 'A changing home, shop, clinic or market to rehearse real life.', icon: Users },
  { title: 'Number & pattern', body: 'Counting, sorting, measuring and puzzles with real objects.', icon: Ruler },
  { title: 'Music & movement', body: 'Instruments, rhythm and space to move.', icon: Music },
  { title: 'Outdoor garden', body: 'Planting, digging and observing nature through the seasons.', icon: Sprout },
];

export const CONTACT_PRINCIPLES = [
  { title: 'Open communication', body: 'Approachable teachers and clear, regular updates about school life.', icon: MessageCircle },
  { title: 'Meaningful progress updates', body: 'Observations, photos of learning and conversations — not just marks.', icon: Eye },
  { title: 'Shared support', body: 'Working together at home and at school around each child’s growth.', icon: Handshake },
];

export const ADMISSIONS_STEPS = [
  { title: 'Send an enquiry', body: 'Tell us a little about your family using the form below.', icon: MessagesSquare },
  { title: 'We get in touch', body: 'A member of the team will contact you by your preferred method.', icon: MessageCircle },
  { title: 'Visit & conversation', body: 'Arrange a visit to see our approach and ask your questions.', icon: CalendarCheck },
  { title: 'Next steps', body: 'When admissions open, we will share the application process with interested families.', icon: Sparkles },
];
