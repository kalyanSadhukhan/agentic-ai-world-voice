import emailjs from '@emailjs/browser';

const EMAILJS_SERVICE_ID = 'service_o9eg6dn';
const EMAILJS_TEMPLATE_ID = 'template_ziw0lzu';
const EMAILJS_ENHANCED_TEMPLATE_ID = 'template_2lybk1p';
const EMAILJS_PUBLIC_KEY = 'CseiQ35P06TXcUbOV';

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

export const sendContactEmail = async (formData: ContactFormData): Promise<void> => {
  try {
    const templateParams = {
      from_name: formData.name,
      reply_to: formData.email,
      company_name: formData.company,
      message: formData.message,
      to_email: 'smartvoiceagentai@gmail.com',
    };

    await emailjs.send(
      EMAILJS_SERVICE_ID,
      EMAILJS_TEMPLATE_ID,
      templateParams,
      EMAILJS_PUBLIC_KEY
    );
  } catch (error) {
    console.error('Error sending email:', error);
    throw new Error('Failed to send email. Please try again.');
  }
};

export const sendEnhancedInquiry = async (formData: EnhancedFormData): Promise<void> => {
  try {
    const templateParams = {
      name: formData.name,
      email: formData.email,
      company: formData.company,
      primaryGoal: formData.primaryGoal,
      agentTypes: formData.agentTypes.join(', '),
      message: formData.message,
    };

    await emailjs.send(
      EMAILJS_SERVICE_ID,
      EMAILJS_ENHANCED_TEMPLATE_ID,
      templateParams,
      EMAILJS_PUBLIC_KEY
    );
  } catch (error) {
    console.error('Error sending enhanced inquiry:', error);
    throw new Error('Failed to send inquiry. Please try again.');
  }
};
