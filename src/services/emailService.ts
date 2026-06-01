export interface ContactFormData {
  name: string;
  email: string;
  company: string;
  message: string;
}

export interface EnhancedFormData extends ContactFormData {
  primaryGoal: string;
  agentTypes: string[];
}

const API_BASE_URL = import.meta.env.VITE_API_URL 
  ? `${import.meta.env.VITE_API_URL}/api/contact` 
  : 'http://localhost:8081/api/contact';

export const sendContactEmail = async (formData: ContactFormData): Promise<void> => {
  try {
    const response = await fetch(`${API_BASE_URL}/simple`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(formData),
    });

    if (!response.ok) {
      throw new Error(`Error: ${response.statusText}`);
    }
  } catch (error) {
    console.error('Error sending email:', error);
    throw new Error('Failed to send email. Please try again.');
  }
};

export const sendEnhancedInquiry = async (formData: EnhancedFormData): Promise<void> => {
  try {
    const response = await fetch(`${API_BASE_URL}/enhanced`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(formData),
    });

    if (!response.ok) {
      throw new Error(`Error: ${response.statusText}`);
    }
  } catch (error) {
    console.error('Error sending enhanced inquiry:', error);
    throw new Error('Failed to send inquiry. Please try again.');
  }
};
