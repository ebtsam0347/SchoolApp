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
    body: tr('Children learn to observe, ask questions and find out how things work.'),
    icon: Lightbulb,
    link: { to: '/learning-approach', label: tr('How inquiry works') },
  },
  {
    id: 'faith',
    title: tr('Faith & Character'),
    body: tr('Kindness, honesty, gratitude and responsibility — lived every day.'),
    icon: Moon,
    link: { to: '/our-philosophy', label: tr('Faith in everyday life') },
  },
  {
    id: 'creativity',
    title: tr('Creativity & Expression'),
    body: tr('Children turn ideas into drawings, models and stories.'),
    icon: Palette,
    link: { to: '/learning-approach', label: tr('Creative learning') },
  },
  {
    id: 'experiential',
    title: tr('Experiential Learning'),
    body: tr('Real materials and real questions — from the garden to the water tray.'),
    icon: FlaskConical,
    link: { to: '/a-day-at-school', label: tr('See a school day') },
  },
  {
    id: 'social',
    title: tr('Emotional & Social Growth'),
    body: tr('Sharing feelings, taking turns and making friends.'),
    icon: Heart,
    link: { to: '/programmes', label: tr('Development by stage') },
  },
  {
    id: 'life',
    title: tr('Life Skills & Safe Technology'),
    body: tr('Independence, self-care and a careful first look at technology.'),
    icon: MonitorSmartphone,
    link: { to: '/programmes', label: tr('Our programmes') },
  },
];

export const APPROACH = [
  {
    step: '01',
    title: tr('Explore'),
    body: tr('Children explore materials, nature, stories and real questions.'),
    photo: 'explore' as PhotoKey,
  },
  {
    step: '02',
    title: tr('Create'),
    body: tr('Children build, draw, try things out and share ideas.'),
    photo: 'create' as PhotoKey,
  },
  {
    step: '03',
    title: tr('Reflect'),
    body: tr('Children talk about what they noticed and what they learned.'),
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
      tr('Noticing everyday blessings — rain, food, a friend’s help — and saying Alhamdulillah with meaning.'),
  },
  {
    arabic: 'صَبْر',
    translit: 'Sabr',
    english: tr('Patience'),
    icon: Hourglass,
    example:
      tr('Waiting for seeds to grow, taking turns, and trying again when a tower falls.'),
  },
  {
    arabic: 'رَحْمَة',
    translit: 'Rahmah',
    english: tr('Kindness & mercy'),
    icon: HandHeart,
    example:
      tr('Comforting a sad friend, caring for living things and using kind words.'),
  },
  {
    arabic: 'أَمَانَة',
    translit: 'Amanah',
    english: tr('Responsibility'),
    icon: ShieldCheck,
    example:
      tr('Looking after shared things, tidying up and watering the class plants.'),
  },
];

export const GALLERY: { photo: PhotoKey; title: string; caption: string }[] = [
  { photo: 'create', title: tr('Building structures'), caption: tr('Balancing, testing and rebuilding — little engineers at work.') },
  { photo: 'garden', title: tr('Gardening & nature'), caption: tr('Planting, watering and watching things grow.') },
  { photo: 'science', title: tr('Practical science'), caption: tr('Simple experiments that start with “What will happen if…?”') },
  { photo: 'story', title: tr('Stories & language'), caption: tr('Stories that grow words, imagination and kindness.') },
  { photo: 'art', title: tr('Art & construction'), caption: tr('Ideas brought to life with paint, clay and recycled materials.') },
  { photo: 'play', title: tr('Cooperative play'), caption: tr('Working together towards one goal.') },
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
    body: tr('Children are welcomed by name and start with a calm activity.'),
    younger: tr('Extra time with a familiar adult and a simple routine.'),
    older: tr('Children sign in and take on small jobs, like checking the plants.'),
    icon: Smile,
    photo: 'admissions',
  },
  {
    title: tr('Morning circle'),
    body: tr('We greet each other, talk about the day and share what we wonder about.'),
    younger: tr('Short circles with songs and movement.'),
    older: tr('Children help lead the circle and plan the day.'),
    icon: Users,
  },
  {
    title: tr('Language and literacy'),
    body: tr('Stories, rhymes and sounds build a love of words and early reading.'),
    younger: tr('Listening games, picture books and conversation.'),
    older: tr('Letter sounds, early writing and retelling stories.'),
    icon: BookOpen,
    photo: 'story',
  },
  {
    title: tr('Hands-on discovery'),
    body: tr('Real investigations with water, plants, light and building — led by children’s questions.'),
    younger: tr('Pouring, filling, sorting and touching.'),
    older: tr('Guessing, testing and recording what they find.'),
    icon: FlaskConical,
    photo: 'science',
  },
  {
    title: tr('Snack and personal care'),
    body: tr('A calm break to practise independence, good manners, Bismillah and Alhamdulillah.'),
    younger: tr('Adults help with handwashing and tidying up.'),
    older: tr('Children serve themselves and help clean up.'),
    icon: Utensils,
  },
  {
    title: tr('Outdoor movement'),
    body: tr('Running, climbing and balancing build strong bodies and confident minds.'),
    younger: tr('Free play, ball games and simple obstacle courses.'),
    older: tr('Team games, garden jobs and nature hunts.'),
    icon: TreePine,
    photo: 'garden',
  },
  {
    title: tr('Creative learning centres'),
    body: tr('Children choose art, building, role play, puzzles or music.'),
    younger: tr('Open-ended materials and lots of conversation.'),
    older: tr('Longer projects that last several days.'),
    icon: Palette,
    photo: 'art',
  },
  {
    title: tr('Islamic stories and reflection'),
    body: tr('Short stories and duas connect Islamic values to everyday life.'),
    younger: tr('Picture stories and simple, repeated duas.'),
    older: tr('Talking about how characters showed kindness, patience or honesty.'),
    icon: Moon,
  },
  {
    title: tr('Sharing and departure'),
    body: tr('Children share their work, tidy up and get ready to go home.'),
    younger: tr('A goodbye song and a calm handover to parents.'),
    older: tr('Sharing one thing they learned and one thing they are thankful for.'),
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
    stage: tr('Learning through the senses, movement and warm, trusting adults.'),
    objectives: [tr('Feel safe and happy away from home'), tr('Start talking and sharing ideas'), tr('Do simple routines on their own')],
    activities: [tr('Sensory trays and water play'), tr('Songs, rhymes and finger play'), tr('Stacking, posting and sorting games')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Lots of talk, picture books and rhymes.') },
      { label: tr('Early mathematics'), body: tr('Big and small, full and empty, and counting songs.') },
      { label: tr('Islamic learning'), body: tr('Hearing Bismillah and Alhamdulillah every day, and short stories.') },
      { label: tr('Social-emotional'), body: tr('Feeling secure, playing alongside others and starting to share.') },
      { label: tr('Creative & physical'), body: tr('Drawing, music, climbing and lots of free movement.') },
    ],
  },
  {
    id: 'nursery',
    name: tr('Nursery'),
    ageBand: tr('around 3–4 years'),
    stage: tr('Full of questions, pretend play and first friendships.'),
    objectives: [tr('Ask questions and explore ideas'), tr('Speak and listen with confidence'), tr('Start to know sounds, shapes and numbers')],
    activities: [tr('Float-or-sink and planting activities'), tr('Pretend play and small-world play'), tr('Block building and simple patterns')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Storytelling, listening games and early sounds.') },
      { label: tr('Early mathematics'), body: tr('Counting real objects, comparing and sorting.') },
      { label: tr('Islamic learning'), body: tr('Short duas, stories of kindness and gratitude, and Allah’s creation in nature.') },
      { label: tr('Social-emotional'), body: tr('Taking turns, naming feelings and solving small problems.') },
      { label: tr('Creative & physical'), body: tr('Painting, modelling, music and balance.') },
    ],
  },
  {
    id: 'kindergarten',
    name: tr('Kindergarten'),
    ageBand: tr('around 4–5 years'),
    stage: tr('More confident, more focused and eager to make, test and explain.'),
    objectives: [tr('Guess, test and explain what they notice'), tr('Begin reading and writing simple words'), tr('Work together on shared projects')],
    activities: [tr('Building and design projects over several days'), tr('Garden and nature journals'), tr('Measuring and comparing')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Phonics through play, early writing and telling stories.') },
      { label: tr('Early mathematics'), body: tr('Numbers, adding and taking away, shapes, measuring and time.') },
      { label: tr('Islamic learning'), body: tr('Stories of the Prophets, daily duas and values like honesty and patience.') },
      { label: tr('Social-emotional'), body: tr('Working together, solving disagreements and caring for others.') },
      { label: tr('Creative & physical'), body: tr('Design and build, drama, music, team games and pencil control.') },
    ],
  },
  {
    id: 'school-readiness',
    name: tr('School Readiness'),
    ageBand: tr('around 5–6 years'),
    stage: tr('Building independence and good learning habits for a confident start at big school.'),
    objectives: [tr('Read and write more independently'), tr('Explain how they solve maths problems'), tr('Look after their things, time and feelings')],
    activities: [tr('Investigation projects with simple notes'), tr('Making books and writing stories'), tr('Class helper jobs')],
    areas: [
      { label: tr('Language & literacy'), body: tr('Reading simple books, writing sentences and speaking to a group.') },
      { label: tr('Early mathematics'), body: tr('Number facts, problem-solving, money and measuring.') },
      { label: tr('Islamic learning'), body: tr('A deeper understanding of Islamic values and manners, and how they guide our choices.') },
      { label: tr('Social-emotional'), body: tr('Staying calm, bouncing back and taking responsibility.') },
      { label: tr('Creative & physical'), body: tr('Creative projects, coordination games and neat handwriting.') },
    ],
  },
];

export const LEARNING_ZONES = [
  { title: tr('Discovery & science'), body: tr('Magnifiers, water and light tables, and natural objects.'), icon: FlaskConical },
  { title: tr('Construction'), body: tr('Blocks and loose parts for little engineers.'), icon: Blocks },
  { title: tr('Reading corner'), body: tr('A cosy corner with picture books and puppets.'), icon: BookOpen },
  { title: tr('Art studio'), body: tr('Paint, clay, collage and recycled materials.'), icon: Palette },
  { title: tr('Role play'), body: tr('A play home, shop or clinic to practise real life.'), icon: Users },
  { title: tr('Number & pattern'), body: tr('Counting, sorting, measuring and puzzles.'), icon: Ruler },
  { title: tr('Music & movement'), body: tr('Instruments, rhythm and space to move.'), icon: Music },
  { title: tr('Outdoor garden'), body: tr('Planting, digging and watching nature through the seasons.'), icon: Sprout },
];

export const CONTACT_PRINCIPLES = [
  { title: tr('Open communication'), body: tr('Friendly teachers and regular updates.'), icon: MessageCircle },
  { title: tr('Meaningful progress updates'), body: tr('Notes, photos and chats — not just marks.'), icon: Eye },
  { title: tr('Shared support'), body: tr('Home and school working together for your child.'), icon: Handshake },
];

export const ADMISSIONS_STEPS = [
  { title: tr('Send an enquiry'), body: tr('Fill in the short form below.'), icon: MessagesSquare },
  { title: tr('We get in touch'), body: tr('Our team contacts you the way you prefer.'), icon: MessageCircle },
  { title: tr('Visit & conversation'), body: tr('Visit us, see the classrooms and ask questions.'), icon: CalendarCheck },
  { title: tr('Next steps'), body: tr('When admissions open, we send you the application details.'), icon: Sparkles },
];
