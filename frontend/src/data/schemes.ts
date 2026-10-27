export interface SchemeInfo {
  id: string
  shortName: string
  fullName: string
  ministry: string
  stage: string
  income: string
  tags: string[]
  prompt: string
}

export const SCHEMES: SchemeInfo[] = [
  {
    id: 'pm-usp-csss',
    shortName: 'PM-USP CSSS',
    fullName: 'Pradhan Mantri Uchchatar Shiksha Protsahan — Central Sector Scheme of Scholarship',
    ministry: 'Dept. of Higher Education, Ministry of Education',
    stage: 'Undergraduate',
    income: 'Family income ≤ ₹4.5 lakh',
    tags: ['UG · 1st degree', 'Regular mode'],
    prompt: 'Am I eligible under PM-USP CSSS?',
  },
  {
    id: 'ishan-uday',
    shortName: 'Ishan Uday',
    fullName: 'Ishan Uday Special Scholarship for the North Eastern Region',
    ministry: 'Ministry of Education',
    stage: 'Undergraduate',
    income: 'Family income < ₹4.5 lakh',
    tags: ['North-East domicile', 'UG · 1st degree'],
    prompt: 'Am I eligible for the Ishan Uday scholarship?',
  },
  {
    id: 'pm-yasasvi',
    shortName: 'PM YASASVI',
    fullName: 'PM YASASVI — Central Sector Scheme of Top Class Education in Schools',
    ministry: 'Dept. of Social Justice & Empowerment',
    stage: 'Classes IX–XII',
    income: 'Family income < ₹2.5 lakh',
    tags: ['OBC / EBC / DNT', 'School'],
    prompt: 'Am I eligible under PM YASASVI Top Class Schools?',
  },
  {
    id: 'top-class-sc',
    shortName: 'Top Class SC',
    fullName: 'Top Class Education Scheme for SC Students',
    ministry: 'Ministry of Social Justice & Empowerment',
    stage: 'UG & PG',
    income: 'Family income ≤ ₹8 lakh',
    tags: ['SC category', 'Notified institutes'],
    prompt: 'Am I eligible for the Top Class Education Scheme for SC students?',
  },
  {
    id: 'top-class-pwd',
    shortName: 'Top Class PWD',
    fullName: 'Top Class Education for Students with Disabilities',
    ministry: 'Dept. of Empowerment of Persons with Disabilities (Divyangjan)',
    stage: 'UG & PG',
    income: 'Family income ≤ ₹8 lakh',
    tags: ['Disability ≥ 40%', 'Notified institutes'],
    prompt: 'Am I eligible for Top Class Education for Students with Disabilities?',
  },
]

export const SUGGESTIONS: string[] = [
  "Am I eligible for PM-USP CSSS? I'm in my first year of BCA with a family income of ₹3 lakh.",
  'What is the income limit for the Ishan Uday scholarship?',
  'Compare PM YASASVI and the Top Class SC scheme.',
  'I have a 60% disability and study in a regular UG course. Am I eligible for Top Class PWD?',
]
