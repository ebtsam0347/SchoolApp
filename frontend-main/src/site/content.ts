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

import { tr } from '@/site/i18n';
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
    alt: tr('Young children exploring natural objects together at a low table'),
    hint: tr('Children investigating leaves, shells and stones'),
    icon: Search,
  },
  wonder: {
    alt: tr('A child looking closely at a leaf through a magnifying glass'),
    hint: tr('A child studying a leaf with a magnifier'),
    icon: Telescope,
  },
  explore: {
    alt: tr('Children exploring a sensory tray of natural materials'),
    hint: tr('Hands in a tray of sand, seeds and pebbles'),
    icon: Compass,
  },
  create: {
    alt: tr('Children building a tall structure with wooden blocks'),
    hint: tr('Building a tower with wooden blocks'),
    icon: Blocks,
  },
  reflect: {
    alt: tr('A small group of children sharing ideas with their teacher on a rug'),
    hint: tr('Children talking about what they noticed'),
    icon: MessagesSquare,
  },
  thinking: {
    alt: tr('Children testing which objects float in a water tray'),
    hint: tr('Testing what floats in a water tray'),
    icon: Droplets,
  },
  garden: {
    alt: tr('Children watering seedlings in a school garden bed'),
    hint: tr('Watering seedlings in the garden'),
    icon: Sprout,
  },
  science: {
    alt: tr('Children mixing colours in clear cups during a simple experiment'),
    hint: tr('A colour-mixing experiment'),
    icon: FlaskConical,
  },
  story: {
    alt: tr('A teacher reading a picture book to a small group of children'),
    hint: tr('Story time with a picture book'),
    icon: BookOpen,
  },
  art: {
    alt: tr('Children painting and constructing with recycled materials'),
    hint: tr('Painting and junk-modelling'),
    icon: Palette,
  },
  play: {
    alt: tr('Children working together to complete a large floor puzzle'),
    hint: tr('Solving a floor puzzle together'),
    icon: Puzzle,
  },
  partnership: {
    alt: tr('A parent and teacher looking at a child’s learning journal together'),
    hint: tr('A parent and teacher sharing a learning journal'),
    icon: Handshake,
  },
  admissions: {
    alt: tr('A welcoming, sunlit early-years classroom with low shelves and plants'),
    hint: tr('A calm, sunlit classroom'),
    icon: Sun,
  },
  classroom: {
    alt: tr('An early-years classroom arranged into learning zones'),
    hint: tr('Classroom learning zones'),
    icon: Shapes,
  },
} satisfies Record<string, PhotoSpec>;

export type PhotoKey = keyof typeof PHOTO_SPECS;
export const PHOTOS: Record<PhotoKey, PhotoSpec> = PHOTO_SPECS;

// ---------------------------------------------------------------------------

export const SEQUENCE = [
  { label: tr('Wonder'), icon: Sparkles },
  { label: tr('Explore'), icon: Compass },
  { label: tr('Discover'), icon: Lightbulb },
  { label: tr('Create'), icon: Brush },
  { label: tr('Grow'), icon: Sprout },
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
    title: tr('Thinking & Discovery'),
    body: tr('Children learn to observe, ask questions, explore possibilities, and discover how things work.'),
    icon: Lightbulb,
    link: { to: '/learning-approach', label: tr('How inquiry works') },
  },
  {
    id: 'faith',
    title: tr('Faith & Character'),
    body: tr('Islamic values come to life through kindness, honesty, gratitude, responsibility, and meaningful stories.'),
    icon: Moon,
    link: { to: '/our-philosophy', label: tr('Faith in everyday life') },
  },
  {
    id: 'creativity',
    title: tr('Creativity & Expression'),
    body: tr('Children turn ideas into drawings, constructions, stories, and imaginative creations.'),
    icon: Palette,
    link: { to: '/learning-approach', label: tr('Creative learning') },
  },
  {
    id: 'experiential',
    title: tr('Experiential Learning'),
    body: tr('Real materials, real questions and real places — from the garden to the water tray.'),
    icon: FlaskConical,
    link: { to: '/a-day-at-school', label: tr('See a school day') },
  },
  {
    id: 'social',
    title: tr('Emotional & Social Growth'),
    body: tr('Naming feelings, taking turns, solving problems together and building friendships.'),
    icon: Heart,
    link: { to: '/programmes', label: tr('Development by stage') },
  },
  {
    id: 'life',
    title: tr('Life Skills & Digital Awareness'),
    body: tr('Independence, self-care and a careful, age-appropriate first look at technology.'),
    icon: MonitorSmartphone,
    link: { to: '/programmes', label: tr('Our programmes') },
  },
];

export const APPROACH = [
  {
    step: '01',
    title: tr('Explore'),
    body: tr('Children investigate materials, nature, stories, and real-world questions.'),
    photo: 'explore' as PhotoKey,
  },
  {
    step: '02',
    title: tr('Create'),
    body: tr('Children build, draw, experiment, imagine, and express ideas.'),
    photo: 'create' as PhotoKey,
  },
  {
    step: '03',
    title: tr('Reflect'),
    body: tr('Children explain what they noticed, consider different possibilities, and learn from experience.'),
    photo: 'reflect' as PhotoKey,
  },
];

export const VALUES = [
  {
    arabic: 'شُكْر',
    translit: 'Shukr',
    english: tr('Gratitude'),
    icon: Sun,
    example:
      tr('Noticing everyday blessings — rain for the garden, food at snack time, a friend’s help — and learning to say Alhamdulillah with meaning.'),
  },
  {
    arabic: 'صَبْر',
    translit: 'Sabr',
    english: tr('Patience'),
    icon: Hourglass,
    example:
      tr('Waiting for seeds to sprout, taking turns with a favourite toy, and trying again when a tower falls down.'),
  },
  {
    arabic: 'رَحْمَة',
    translit: 'Rahmah',
    english: tr('Kindness & mercy'),
    icon: HandHeart,
    example:
      tr('Comforting a classmate who is upset, caring gently for living things, and choosing kind words.'),
  },
  {
    arabic: 'أَمَانَة',
    translit: 'Amanah',
    english: tr('Responsibility'),
    icon: ShieldCheck,
    example:
      tr('Looking after shared materials, tidying learning spaces, and taking turns to water the class plants.'),
  },
];

export const GALLERY: { photo: PhotoKey; title: string; caption: string }[] = [
  { photo: 'create', title: tr('Building structures'), caption: tr('Balancing, testing and rebuilding — early engineering in action.') },
  { photo: 'garden', title: tr('Gardening & nature'), caption: tr('Planting, watering and watching patiently as things grow.') },
  { photo: 'science', title: tr('Practical science'), caption: tr('Simple experiments that start with “What will happen if…?”') },
  { photo: 'story', title: tr('Stories & language'), caption: tr('Rich stories that grow vocabulary, imagination and empathy.') },
  { photo: 'art', title: tr('Art & construction'), caption: tr('Ideas made visible with paint, clay and recycled materials.') },
  { photo: 'play', title: tr('Cooperative play'), caption: tr('Sharing a goal, negotiating roles and solving it together.') },
];

export const DAY_GLIMPSE = [
  { title: tr('Arrive & Connect'), body: tr('A warm welcome, greetings and settling in with friends.'), icon: Smile },
  { title: tr('Wonder & Explore'), body: tr('A question, object or story sparks the day’s curiosity.'), icon: Sparkles },
  { title: tr('Learn & Discover'), body: tr('Hands-on activities in language, number and the world around us.'), icon: Lightbulb },
  { title: tr('Move & Play'), body: tr('Outdoor movement, games and fresh air.'), icon: Footprints },
  { title: tr('Create & Collaborate'), body: tr('Building, making and working on ideas together.'), icon: Users },
  { title: tr('Reflect & Share'), body: tr('Talking about what we noticed, learned and are thankful for.'), icon: MessageCircle },
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
    title: tr('Arrival and settling in'),
    body: tr('Children are greeted by name, hang up their belongings and choose a calm activity while friends arrive.'),
    younger: tr('Extra time with a familiar adult; a simple, predictable routine helps them feel secure.'),
    older: tr('Children self-register and take on small responsibilities such as checking the plants.'),
    icon: Smile,
    photo: 'admissions',
  },
  {
    title: tr('Morning circle'),
    body: tr('The group gathers to greet each other, talk about the day and share something they are curious about.'),
    younger: tr('Short, song-rich circles with lots of movement.'),
    older: tr('Children lead parts of the circle and help plan the day.'),
    icon: Users,
  },
  {
    title: tr('Language and literacy'),
    body: tr('Stories, rhymes, sounds and mark-making build a love of words and early reading and writing.'),
    younger: tr('Listening games, picture books and conversation.'),
    older: tr('Letter sounds, early writing and retelling stories in their own words.'),
    icon: BookOpen,
    photo: 'story',
  },
  {
    title: tr('Hands-on discovery'),
    body: tr('Investigations with real materials — water, plants, light, shapes and building — guided by children’s questions.'),
    younger: tr('Sensory exploration: pouring, filling, sorting and feeling.'),
    older: tr('Predicting, testing and recording what they find.'),
    icon: FlaskConical,
    photo: 'science',
  },
  {
    title: tr('Snack and personal care'),
    body: tr('A calm, social break where children practise independence, good manners and saying Bismillah and Alhamdulillah.'),
    younger: tr('Adults support handwashing, opening containers and tidying up.'),
    older: tr('Children serve themselves, help set up and clean up.'),
    icon: Utensils,
  },
  {
    title: tr('Outdoor movement'),
    body: tr('Running, climbing, balancing and exploring the outdoors build strong bodies and confident minds.'),
    younger: tr('Free movement, ball play and gentle obstacle paths.'),
    older: tr('Team games, garden jobs and nature hunts.'),
    icon: TreePine,
    photo: 'garden',
  },
  {
    title: tr('Creative learning centres'),
    body: tr('Children choose between art, construction, role play, puzzles and music to follow their ideas.'),
    younger: tr('Open-ended materials with lots of adult conversation.'),
    older: tr('Longer projects that continue over several days.'),
    icon: Palette,
    photo: 'art',
  },
  {
    title: tr('Islamic stories and reflection'),
    body: tr('Age-appropriate stories, short duas and gentle discussion connect Islamic values to children’s everyday lives.'),
    younger: tr('Short stories with pictures and simple, repeated duas.'),
    older: tr('Talking about how characters showed kindness, patience or honesty.'),
    icon: Moon,
  },
  {
    title: tr('Sharing and departure'),
    body: tr('Children share something they made or noticed, tidy up together and get ready to go home.'),
    younger: tr('A goodbye song and a calm handover to families.'),
    older: tr('Reflecting on one thing they learned and one thing they are thankful for.'),
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
    name: tr('Early Years'),
    ageBand: tr('around 2–3 years'),
    stage: tr('Discovering the world through the senses, movement and close, trusting relationships with caring adults.'),
    objectives: [tr('Feel safe, settled and happy away from home'), tr('Develop early language and communication'), tr('Build independence in simple routines')],
    activities: [tr('Sensory trays and water play'), tr('Songs, rhymes and finger play'), tr('Simple stacking, posting and sorting')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Rich conversation, picture books, naming objects and joining in with rhymes.') },
      { label: tr('Early mathematics'), body: tr('Noticing big and small, filling and emptying, and counting in songs.') },
      { label: tr('Islamic learning'), body: tr('Hearing Bismillah and Alhamdulillah in daily routines and short, gentle stories.') },
      { label: tr('Social-emotional'), body: tr('Forming secure attachments, playing alongside others and beginning to share.') },
      { label: tr('Creative & physical'), body: tr('Mark-making, music, crawling, climbing and lots of free movement.') },
    ],
  },
  {
    id: 'nursery',
    name: tr('Nursery'),
    ageBand: tr('around 3–4 years'),
    stage: tr('Asking endless questions, playing imaginatively and beginning to play cooperatively with friends.'),
    objectives: [tr('Ask questions and explore ideas'), tr('Develop confident speaking and listening'), tr('Begin to recognise sounds, shapes and numbers')],
    activities: [tr('Float-or-sink and planting investigations'), tr('Role play and small-world play'), tr('Block building and simple patterns')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Storytelling, listening games, early sounds and enjoying books together.') },
      { label: tr('Early mathematics'), body: tr('Counting real objects, comparing sizes, sorting and spotting patterns.') },
      { label: tr('Islamic learning'), body: tr('Short duas, stories of kindness and gratitude, and learning about Allah’s creation in nature.') },
      { label: tr('Social-emotional'), body: tr('Taking turns, naming feelings and solving small problems with friends.') },
      { label: tr('Creative & physical'), body: tr('Painting, modelling, music, balancing and developing pencil grip through play.') },
    ],
  },
  {
    id: 'kindergarten',
    name: tr('Kindergarten'),
    ageBand: tr('around 4–5 years'),
    stage: tr('Growing confidence, longer attention and a strong desire to make, test and explain.'),
    objectives: [tr('Predict, test and explain what they notice'), tr('Begin reading and writing simple words'), tr('Work together on shared projects')],
    activities: [tr('Multi-day building and design projects'), tr('Garden and nature journals'), tr('Measuring, comparing and simple data')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Phonics through play, early writing, retelling stories and presenting ideas.') },
      { label: tr('Early mathematics'), body: tr('Number sense, adding and taking away with objects, shapes, measuring and time.') },
      { label: tr('Islamic learning'), body: tr('Stories of the Prophets, everyday duas and reflecting on values such as honesty and patience.') },
      { label: tr('Social-emotional'), body: tr('Collaborating, resolving disagreements and showing care for others.') },
      { label: tr('Creative & physical'), body: tr('Design-and-build, drama, music, team games and fine-motor skills for writing.') },
    ],
  },
  {
    id: 'school-readiness',
    name: tr('School Readiness'),
    ageBand: tr('around 5–6 years'),
    stage: tr('Building independence, resilience and the learning habits that support a confident move into formal schooling.'),
    objectives: [tr('Read and write with growing independence'), tr('Reason and explain mathematical thinking'), tr('Manage belongings, time and feelings')],
    activities: [tr('Investigation projects with simple recording'), tr('Book-making and story-writing'), tr('Responsibility roles in the classroom')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Reading simple books, writing sentences and speaking confidently to a group.') },
      { label: tr('Early mathematics'), body: tr('Number bonds, problem-solving, money, measuring and explaining strategies.') },
      { label: tr('Islamic learning'), body: tr('Deepening understanding of Islamic values, manners and stories, and how they guide our choices.') },
      { label: tr('Social-emotional'), body: tr('Resilience, self-regulation and taking responsibility for actions.') },
      { label: tr('Creative & physical'), body: tr('Extended creative projects, coordination games and confident handwriting.') },
    ],
  },
];

export const LEARNING_ZONES = [
  { title: tr('Discovery & science'), body: tr('Magnifiers, water and light tables, natural objects and simple tools.'), icon: FlaskConical },
  { title: tr('Construction'), body: tr('Blocks, loose parts and design challenges for early engineers.'), icon: Blocks },
  { title: tr('Reading corner'), body: tr('A cosy space with picture books, storytelling props and puppets.'), icon: BookOpen },
  { title: tr('Art studio'), body: tr('Paint, clay, collage and recycled materials always within reach.'), icon: Palette },
  { title: tr('Role play'), body: tr('A changing home, shop, clinic or market to rehearse real life.'), icon: Users },
  { title: tr('Number & pattern'), body: tr('Counting, sorting, measuring and puzzles with real objects.'), icon: Ruler },
  { title: tr('Music & movement'), body: tr('Instruments, rhythm and space to move.'), icon: Music },
  { title: tr('Outdoor garden'), body: tr('Planting, digging and observing nature through the seasons.'), icon: Sprout },
];

export const CONTACT_PRINCIPLES = [
  { title: tr('Open communication'), body: tr('Approachable teachers and clear, regular updates about school life.'), icon: MessageCircle },
  { title: tr('Meaningful progress updates'), body: tr('Observations, photos of learning and conversations — not just marks.'), icon: Eye },
  { title: tr('Shared support'), body: tr('Working together at home and at school around each child’s growth.'), icon: Handshake },
];

export const ADMISSIONS_STEPS = [
  { title: tr('Send an enquiry'), body: tr('Tell us a little about your family using the form below.'), icon: MessagesSquare },
  { title: tr('We get in touch'), body: tr('A member of the team will contact you by your preferred method.'), icon: MessageCircle },
  { title: tr('Visit & conversation'), body: tr('Arrange a visit to see our approach and ask your questions.'), icon: CalendarCheck },
  { title: tr('Next steps'), body: tr('When admissions open, we will share the application process with interested families.'), icon: Sparkles },
];
