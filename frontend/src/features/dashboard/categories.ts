import HeadphonesRounded from '@mui/icons-material/HeadphonesRounded';
import SportsEsportsOutlined from '@mui/icons-material/SportsEsportsOutlined';
import SchoolOutlined from '@mui/icons-material/SchoolOutlined';
import ForumOutlined from '@mui/icons-material/ForumOutlined';

export const categories = [
  { name: 'Podcast', description: 'Conversations and shows', Icon: HeadphonesRounded, tone: 'violet' },
  { name: 'Gaming', description: 'Streams and gameplay', Icon: SportsEsportsOutlined, tone: 'blue' },
  { name: 'Education', description: 'Lessons and tutorials', Icon: SchoolOutlined, tone: 'green' },
  { name: 'Interview', description: 'Q&As and discussions', Icon: ForumOutlined, tone: 'amber' },
] as const;

export type Category = (typeof categories)[number]['name'];
